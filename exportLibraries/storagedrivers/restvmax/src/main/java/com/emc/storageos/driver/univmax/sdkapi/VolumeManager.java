/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.sdkapi;

import com.emc.storageos.driver.univmax.DriverDataUtil;
import com.emc.storageos.driver.univmax.DriverUtil;
import com.emc.storageos.driver.univmax.rest.EndPoint;
import com.emc.storageos.driver.univmax.rest.JsonUtil;
import com.emc.storageos.driver.univmax.rest.RestClient;
import com.emc.storageos.driver.univmax.rest.type.common.*;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.CreateStorageGroupParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.GetVolumeResultType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.SloBasedStorageGroupParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.VolumeType;
import com.emc.storageos.storagedriver.DefaultDriverTask;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class VolumeManager {

    private static final Logger LOG = LoggerFactory.getLogger(VolumeManager.class);

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
        task.setStatus(DriverTask.TaskStatus.READY);

        try {
            for (StorageVolume volume : volumes) {
                // RESTful API POST
                client = driverDataUtil.getRestClientByStorageSystemId(volume.getStorageSystemId());
                postCreateVolume(volume);
                // RESTful API GET: get volume ID
                String getResponse = doGetVolumeId(volume);
                // RESTful API GET: use volume ID to retrieve volume details.
                String details = doGetVolumeDetails(volume, getResponse);

                // Fill value into volume.
                fileValueIntoVolume(details, volume);
            }
            task.setMessage("Volumes creation successful.");
        } catch (Exception e) {
            task.setMessage(DriverUtil.getStackTrace(e));
            // fixme: detect FAILED or PARTIALLY_FAILED.
            task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
        }

        return task;
    }

    private String postCreateVolume(StorageVolume volume) {
        // do post http://10.247.97.150:8443/univmax/restapi/sloprovisoning/symmetrix/{symmetrixid}/storagegroup
        // create a volume with storageGroup
        VolumeAttributeType aType = new VolumeAttributeType(CapacityUnitType.MB,
                String.valueOf(volume.getRequestedCapacity() / (1024 * 1024)));
        SloBasedStorageGroupParamType[] sloType = new SloBasedStorageGroupParamType[1];
        for (int i = 0; i < sloType.length; i++) {
            sloType[i] = new SloBasedStorageGroupParamType(new Long(1), aType);
        }
        CreateStorageGroupParamType groupParamType = new CreateStorageGroupParamType();
        groupParamType.setStorageGroupId(volume.getStorageGroupId());
        groupParamType.setSrpId(volume.getStoragePoolId());
        groupParamType.setSloBasedStorageGroupParam(sloType);
        String restParam = JsonUtil.toJsonString(groupParamType);

        return client.getJsonString(RestClient.METHOD.POST,
                String.format(EndPoint.SLOPROVISIONING_SYMMETRIX__STORAGEGROUP, volume.getStorageSystemId()),
                restParam);
    }

    private String doGetVolumeId(StorageVolume volume) {
        return client.getJsonString(RestClient.METHOD.GET,
                String.format(EndPoint.SLOPROVISIONING_SYMMETRIX__VOLUME_QUERY,
                        volume.getStorageSystemId(), "storageGroupId", volume.getStorageGroupId()));
    }

    private String doGetVolumeDetails(StorageVolume volume, String getResponse) {
        IteratorType<VolumesListType> iType = JsonUtil.fromJsonIter(getResponse,
                new TypeToken<IteratorType<VolumesListType>>() {
                }.getType());
        ResultListType<VolumesListType> resultList = iType.getResultList();
        String volumeId = resultList.getResult()[0].getVolumeId();
        String getVolumePath = String.format(EndPoint.SLOPROVISIONING_SYMMETRIX__VOLUME_ID,
                volume.getStorageSystemId(), volumeId);

        return client.getJsonString(RestClient.METHOD.GET, getVolumePath);
    }

    private void fileValueIntoVolume(String details, StorageVolume volume) {
        GetVolumeResultType getVolumeType = JsonUtil.fromJson(details, GetVolumeResultType.class);
        VolumeType typeArray = getVolumeType.getVolume()[0];
        volume.setProvisionedCapacity((new Double(typeArray.getCap_mb() * 1024 * 1024)).longValue());
        volume.setAllocatedCapacity(volume.getProvisionedCapacity() * typeArray.getAllocated_percent());
        volume.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
        volume.setNativeId(typeArray.getVolumeId());
        volume.setWwn(typeArray.getWwn());
    }
}
