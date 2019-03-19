/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.cinder.job;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.CinderEndPointInfo;
import com.emc.storageos.cinder.api.CinderApi;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeExpandCompleter;
import com.emc.storageos.volumecontroller.impl.cinder.CinderUtils;

public class CinderVolumeExpandJob extends CinderJob {

    private static final long serialVersionUID = -1005208786425264847L;
    private static final Logger _logger = LoggerFactory
            .getLogger(CinderVolumeExpandJob.class);
    private URI storagePoolUri = null;

    public CinderVolumeExpandJob(String jobId, String jobName,
            URI storageSystem, String componentType, CinderEndPointInfo ep,
            TaskCompleter taskCompleter, URI storagePoolUri) {
        super(jobId, "ExpandVolume:VolumeName:" + jobName, storageSystem,
                componentType, ep, taskCompleter);
        this.storagePoolUri = storagePoolUri;
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        DbClient dbClient = jobContext.getDbClient();
        try {
            // Do nothing if the job is not completed yet
            if (status == JobStatus.IN_PROGRESS) {
                return;
            }

            String opId = getTaskCompleter().getOpId();
            _logger.info(String.format("Updating status of job %s to %s",
                    opId, status.name()));

            StorageSystem storageSystem = dbClient.queryObject(
                    StorageSystem.class, getStorageSystemURI());

            CinderApi cinderApi = jobContext.getCinderApiFactory().getApi(
                    storageSystem.getActiveProviderURI(), getEndPointInfo());

            URI volumeId = getTaskCompleter().getId();

            // If terminal state update storage pool capacity and remove reservation for volume capacity
            // from pool's reserved capacity map.
            StoragePool storagePool = null;
            if (status == JobStatus.SUCCESS || status == JobStatus.FAILED)
            {
                storagePool = dbClient.queryObject(StoragePool.class, storagePoolUri);
                StringMap reservationMap = storagePool.getReservedCapacityMap();
                // remove from reservation map
                reservationMap.remove(volumeId.toString());
                dbClient.persistObject(storagePool);
            }

            if (status == JobStatus.SUCCESS)
            {
                VolumeExpandCompleter taskCompleter = (VolumeExpandCompleter) getTaskCompleter();
                Volume volume = dbClient.queryObject(Volume.class, taskCompleter.getId());

                long oldCapacity = volume.getCapacity();
                long newCapacity = taskCompleter.getSize();
                // set requested capacity
                volume.setCapacity(newCapacity);
                volume.setProvisionedCapacity(taskCompleter.getSize());
                volume.setAllocatedCapacity(taskCompleter.getSize());
                dbClient.persistObject(volume);

                long increasedCapacity = newCapacity - oldCapacity;
                CinderUtils.updateStoragePoolCapacity(dbClient, cinderApi, storagePool,
                        String.valueOf(increasedCapacity / CinderConstants.BYTES_TO_GB), false);
            }

        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for CinderExpandVolumeJob", e);
            setErrorStatus("Encountered an internal error during expand volume job status processing : "
                    + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

    @Override
    protected boolean isJobSucceeded(String currentStatus) {
        return (CinderConstants.ComponentStatus.AVAILABLE.getStatus().equalsIgnoreCase(currentStatus));
    }

    @Override
    protected boolean isJobFailed(String currentStatus) {
        return (CinderConstants.ComponentStatus.ERROR.getStatus().equalsIgnoreCase(currentStatus) || CinderConstants.ComponentStatus.ERROR_EXTENDING
                .getStatus().equalsIgnoreCase(currentStatus));
    }

}
