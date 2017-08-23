/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.sdkapi;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.univmax.helper.DriverDataUtil;
import com.emc.storageos.driver.univmax.helper.DriverUtil;
import com.emc.storageos.driver.univmax.rest.EndPoint;
import com.emc.storageos.driver.univmax.rest.RestClient;
import com.emc.storageos.driver.univmax.rest.type.common.CapacityUnitType;
import com.emc.storageos.driver.univmax.rest.type.common.CreateStorageEmulationType;
import com.emc.storageos.driver.univmax.rest.type.common.ExecutionOption;
import com.emc.storageos.driver.univmax.rest.type.common.JobStatus;
import com.emc.storageos.driver.univmax.rest.type.common.VolumeAttributeType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning84.AddVolumeParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning84.CreateStorageGroupParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning84.EditStorageGroupActionParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning84.EditStorageGroupParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning84.ExpandStorageGroupParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning84.StorageGroupType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning84.VolumeIdentifierChoiceType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning84.VolumeIdentifierType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning84.VolumeType;
import com.emc.storageos.driver.univmax.rest.type.system84.JobType;
import com.emc.storageos.storagedriver.DefaultDriverTask;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

public class VolumeManager {

    private static final Logger log = LoggerFactory.getLogger(VolumeManager.class);
    private RestClient client;

    // TODO: Implement this interface.
    public DriverTask createVolumes(DriverDataUtil driverDataUtil,
            List<StorageVolume> volumes, StorageCapabilities capabilities) {

        String driverName = driverDataUtil.getDriverName();
        String taskId = String.format("%s+%s+%s", driverName, "create-storage-volumes", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        if (volumes.isEmpty()) {
            task.setMessage("Input volume list is empty.");
            task.setStatus(DriverTask.TaskStatus.WARNING);
            return task;
        }
        String msg = "Volumes Creation: ";
        task.setStatus(DriverTask.TaskStatus.READY);

        try {
            // fixme: how to use the parameter "capabilities" ?
            for (StorageVolume volume : volumes) {
                client = driverDataUtil.getRestClientByStorageSystemId(volume.getStorageSystemId());

                // step 1: ensure a valid storage group
                try {
                    // Check if storage group exists.
                    client.get(
                            StorageGroupType.class,
                            String.format(EndPoint.SLOPROVISIONING84_SYMMETRIX_ID_STORAGEGROUP_ID,
                                    volume.getStorageSystemId(),
                                    volume.getStorageGroupId()));

                    log.info("Storage group {} exists.", volume.getStorageGroupId());
                } catch (NoSuchElementException e) {
                    // Storage group does not exist, create a new storage group.
                    CreateStorageGroupParamType createStorageGroupParam = new CreateStorageGroupParamType();
                    createStorageGroupParam.setExecutionOption(ExecutionOption.ASYNCHRONOUS);
                    createStorageGroupParam.setStorageGroupId(volume.getStorageGroupId());
                    createStorageGroupParam.setSrpId(volume.getStoragePoolId());
                    // fixme: where to find the emulation type ?
                    createStorageGroupParam.setEmulation(CreateStorageEmulationType.FBA);
                    log.info("Storage group {} does not exist, create a new one ....", volume.getStorageGroupId());
                    JobType job = client.post(
                            JobType.class,
                            String.format(EndPoint.SLOPROVISIONING84_SYMMETRIX_ID_STORAGEGROUP,
                                    volume.getStorageSystemId()),
                            createStorageGroupParam);

                    /*
                     * Job could fail because
                     * - low level Exception, such as http error, unisphere error or Vmax error.
                     * - failed due to race condition or heavy load on the array
                     * (Vmax locking, timeout, memory exhausted, etc).
                     * - ConcurrentModificationException: due to another thread has created the same storage group,
                     * in this case, the code is actually good to go.
                     */
                    try {
                        waitForJob(String.format(EndPoint.SYSTEM84_SYMMETRIX_ID_JOB_ID,
                                volume.getStorageSystemId(), job.getJobId()));

                        log.info("Storage group {} has been successfully created.", volume.getStorageGroupId());
                    } catch (Exception ex) {
                        log.error("Creating storage group {} got exception: ", volume.getStorageGroupId(), ex);
                    }

                    // Check storage group existence anyways (see above comment).
                    client.get(
                            StorageGroupType.class,
                            String.format(EndPoint.SLOPROVISIONING84_SYMMETRIX_ID_STORAGEGROUP_ID,
                                    volume.getStorageSystemId(),
                                    volume.getStorageGroupId()));
                    log.info("Storage group {} is now created.", volume.getStorageGroupId());
                }

                // step 2: create volume inside the storage group
                EditStorageGroupParamType editParam = new EditStorageGroupParamType();
                EditStorageGroupActionParamType editActionParam = new EditStorageGroupActionParamType();
                ExpandStorageGroupParamType expandParam = new ExpandStorageGroupParamType();
                AddVolumeParamType addVolumeParam = new AddVolumeParamType();
                VolumeAttributeType volumeAttribute = new VolumeAttributeType();
                VolumeIdentifierType volumeIdentifier = new VolumeIdentifierType();

                editParam.setEditStorageGroupActionParam(editActionParam);
                editActionParam.setExpandStorageGroupParam(expandParam);
                expandParam.setAddVolumeParam(addVolumeParam);
                addVolumeParam.setVolumeAttribute(volumeAttribute);
                addVolumeParam.setVolumeIdentifier(volumeIdentifier);

                editParam.setExecutionOption(ExecutionOption.ASYNCHRONOUS);
                addVolumeParam.setNum_of_vols(Long.valueOf(1));
                addVolumeParam.setCreate_new_volumes(true);
                // fixme: how to choose the emulation type?
                addVolumeParam.setEmulation(CreateStorageEmulationType.FBA);
                volumeAttribute.setCapacityUnit(CapacityUnitType.MB);
                volumeAttribute.setVolume_size(String.valueOf(volume.getRequestedCapacity() / (1024 * 1024)));
                volumeIdentifier.setVolumeIdentifierChoice(VolumeIdentifierChoiceType.identifier_name_plus_volume_id);
                volumeIdentifier.setIdentifier_name(volume.getDisplayName());

                log.info("Creating volume in storage group {} ....", volume.getStorageGroupId());
                JobType job = client.put(
                        JobType.class,
                        String.format(EndPoint.SLOPROVISIONING84_SYMMETRIX_ID_STORAGEGROUP_ID,
                                volume.getStorageSystemId(), volume.getStorageGroupId()),
                        editParam);

                // For the time being, we may not be able to recover if the job fails.
                job = waitForJob(String.format(EndPoint.SYSTEM84_SYMMETRIX_ID_JOB_ID,
                        volume.getStorageSystemId(), job.getJobId()));

                // step 3: get volume information and fill it into input parameter.
                // fixme: for unisphere 8.4, we have to use this workaround to get volume id, see OPT 524552.
                // example string:
                // "description": "Creating new Volumes for restapi_test_20170814_1 : [00486]"
                String[] vidStr = job.getTask()[0].getDescription().trim().split("]");
                String[] vidStr1 = vidStr[vidStr.length - 1].trim().split("\\[");
                String vid = vidStr1[vidStr1.length - 1];
                log.info("Volume created, ID: {}.", vid);

                // get volume detail
                VolumeType vol = client.get(
                        VolumeType.class,
                        String.format(EndPoint.SLOPROVISIONING84_SYMMETRIX_ID_VOLUME_ID,
                                volume.getStorageSystemId(), vid));

                // fill value into volume
                volume.setNativeId(vol.getVolumeId());
                if (vol.getHas_effective_wwn()) {
                    volume.setWwn(vol.getWwn());
                }
                // fixme: ?? check vol.getStatus() ??
                volume.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
                if (!vol.getStatus().equals("Ready")) {
                    volume.setAccessStatus(StorageObject.AccessStatus.NOT_READY);
                }
                // fixme: ?? check vol.getType() ??
                volume.setThinlyProvisioned(true);
                if (!vol.getType().equals("TDEV")) {
                    volume.setThinlyProvisioned(false);
                }
                volume.setProvisionedCapacity(new Double(vol.getCap_mb() * 1024 * 1024).longValue());
                volume.setAllocatedCapacity(volume.getProvisionedCapacity() * vol.getAllocated_percent());
            }
            msg += "success.";
            log.info(msg);
            task.setMessage(msg);
        } catch (Exception e) {
            msg += "fail.\n" + DriverUtil.getStackTrace(e);
            log.error(msg);
            task.setMessage(msg);
            task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
        }

        return task;
    }

    private JobType waitForJob(String jobEndPoint) {
        int counter = 200;
        JobType job;
        do {
            job = client.get(JobType.class, jobEndPoint);

            if (job.getStatus().equals(JobStatus.SUCCEEDED)) {
                log.info("job: " + job.getJobId() + ", endpoint: " + jobEndPoint + ", query count: " + counter);
                break;
            }
            counter--;
        } while (counter > 0);

        if (!job.getStatus().equals(JobStatus.SUCCEEDED)) {
            String msg = "Job: " + job.getJobId() + ", query count exceeds 200.";
            log.error(msg);
            throw new RuntimeException(msg);
        }

        return job;
    }
}