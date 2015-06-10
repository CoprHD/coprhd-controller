/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.hds.prov.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.model.LogicalUnit;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeExpandCompleter;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;
import com.emc.storageos.workflow.WorkflowService;

import org.milyn.payload.JavaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;


/**
 * Job for volumeExpand operation.
 *
 */
public class HDSVolumeExpandJob extends HDSJob {
    private static final Logger _log = LoggerFactory.getLogger(HDSVolumeExpandJob.class);

    URI storagePoolURI;

    public HDSVolumeExpandJob(String jobId, URI storageSystem, URI storagePool,
            TaskCompleter taskCompleter, String jobName) {
        super(jobId, storageSystem, taskCompleter, "VolumeExpand");
        storagePoolURI = storagePool;
    }

    /**
     * Called to update the job status when the volume expand job completes.
     * 
     * @param jobContext
     *            The job context.
     */
    public void updateStatus(JobContext jobContext) throws Exception {
        LogicalUnit logicalUnit = null;
        try {

            if (_status == JobStatus.IN_PROGRESS) {
                return;
            }
            DbClient dbClient = jobContext.getDbClient();
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class,
                    getStorageSystemURI());

            HDSApiClient hdsApiClient = jobContext.getHdsApiFactory().getClient(
                    HDSUtils.getHDSServerManagementServerInfo(storageSystem),
                    storageSystem.getSmisUserName(), storageSystem.getSmisPassword());

            JavaResult javaResult = hdsApiClient
                    .checkAsyncTaskStatus(getHDSJobMessageId());

            // If terminal state update storage pool capacity and remove
            // reservation for volume capacity
            // from pool's reserved capacity map.
            if (_status == JobStatus.SUCCESS || _status == JobStatus.FAILED) {
                StoragePool storagePool = dbClient.queryObject(StoragePool.class,
                        storagePoolURI);
                HDSUtils.updateStoragePoolCapacity(dbClient, hdsApiClient, storagePool);
                StringMap reservationMap = storagePool.getReservedCapacityMap();
                URI volumeId = getTaskCompleter().getId();
                // remove from reservation map
                reservationMap.remove(volumeId.toString());
                dbClient.persistObject(storagePool);
            }

            String opId = getTaskCompleter().getOpId();
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "Updating status of job %s to %s, task: %s", this.getJobName(),
                    _status.name(), opId));

            if (_status == JobStatus.SUCCESS) {
                VolumeExpandCompleter taskCompleter = (VolumeExpandCompleter) getTaskCompleter();
                Volume volume = dbClient.queryObject(Volume.class, taskCompleter.getId());
                // set requested capacity
                volume.setCapacity(taskCompleter.getSize());
                // set meta related properties
                volume.setIsComposite(taskCompleter.isComposite());
                volume.setCompositionType(taskCompleter.getMetaVolumeType());

                logicalUnit = (LogicalUnit) javaResult.getBean("logicalunit");
                if (null != logicalUnit) {
                    long capacityInBytes = (Long.valueOf(logicalUnit.getCapacityInKB())) * 1024L;
                    volume.setProvisionedCapacity(capacityInBytes);
                    volume.setAllocatedCapacity(capacityInBytes);
                }

                logMsgBuilder
                        .append(String
                                .format("\n   Capacity: %s, Provisioned capacity: %s, Allocated Capacity: %s",
                                        volume.getCapacity(),
                                        volume.getProvisionedCapacity(),
                                        volume.getAllocatedCapacity()));
                if (volume.getIsComposite()) {
                    logMsgBuilder
                            .append(String
                                    .format("\n   Is Meta: %s, Total meta member capacity: %s, Meta member count %s, Meta member size: %s",
                                            volume.getIsComposite(),
                                            volume.getTotalMetaMemberCapacity(),
                                            volume.getMetaMemberCount(),
                                            volume.getMetaMemberSize()));
                }

                _log.info(logMsgBuilder.toString());

                dbClient.persistObject(volume);
                // Reset list of meta members native ids in WF data (when meta
                // is created meta members are removed from array)
                WorkflowService.getInstance()
                        .storeStepData(opId, new ArrayList<String>());
            }
        } catch (Exception e) {
            _log.error(
                    "Caught an exception while trying to updateStatus for HDSVolumeExpandJob",
                    e);
            setErrorStatus("Encountered an internal error during volume expand job status processing : "
                    + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }
}
