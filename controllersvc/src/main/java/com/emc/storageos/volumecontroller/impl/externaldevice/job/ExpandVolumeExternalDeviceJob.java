/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice.job;

import java.net.URI;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.task.ExpandVolumeDriverTask;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.ExternalBlockStorageDevice;
import com.emc.storageos.volumecontroller.impl.externaldevice.ExternalDeviceUtils;

/**
 * This ExternalDeviceJob derived class is created to monitor the progress
 * of a request to a expand a storage volume that will complete asynchronously.
 */
public class ExpandVolumeExternalDeviceJob extends ExternalDeviceJob {
    
    private static final long serialVersionUID = 1L;
    
    // The URI of the volume being expanded.
    private URI _volumeURI;

    // Logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(ExpandVolumeExternalDeviceJob.class);

    /**
     * Constructor.
     * 
     * @param storageSystemURI The URI of the external storage system on which the task is running.
     * @param volumeURI The URI of the volume being expanded.
     * @param driverTaskId The id of the task monitored by the job.
     * @param taskCompleter The task completer.
     */
    public ExpandVolumeExternalDeviceJob(URI storageSystemURI, URI volumeURI, String driverTaskId,
            TaskCompleter taskCompleter) {
        super(storageSystemURI, driverTaskId, taskCompleter);
        _volumeURI = volumeURI;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doTaskSucceeded(DriverTask driverTask, DbClient dbClient) throws Exception {
        // Get the ViPR volume.
        s_logger.info(String.format("Successfully expanded volume %s:%s", _volumeURI, driverTask.getMessage()));
        Volume volume = dbClient.queryObject(Volume.class, _volumeURI);
        if (volume == null) {
            s_logger.error(String.format("Failed to find volume %s", _volumeURI));
            throw DeviceControllerException.exceptions.objectNotFound(_volumeURI);
        }
        
        // Update the ViPR volume with the driver volume information.
        ExpandVolumeDriverTask expandVolumeDriverTask = (ExpandVolumeDriverTask) driverTask;
        StorageVolume updatedDeviceVolume = expandVolumeDriverTask.getStorageVolume();
        ExternalDeviceUtils.updateExpandedVolume(volume, updatedDeviceVolume, dbClient);

        try {
            // Update storage pool capacity in database.
            ExternalDeviceUtils.updateStoragePoolCapacityAfterOperationComplete(volume.getPool(), _storageSystemURI,
                    Collections.singletonList(_volumeURI), dbClient);
        } catch (Exception ex) {
            s_logger.error("Failed to update storage pool {} after volume expand operation completion.", volume.getPool(), ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doTaskFailed(DriverTask driverTask, DbClient dbClient) throws Exception {
        s_logger.error(String.format("Failed to expand volume %s:%s", _volumeURI, driverTask.getMessage()));

        // Update storage pool capacity in database.
        Volume volume = dbClient.queryObject(Volume.class, _volumeURI);
        if (volume == null) {
            s_logger.error(String.format("Failed to find volume %s", _volumeURI));
            throw DeviceControllerException.exceptions.objectNotFound(_volumeURI);
        } else {
            try {
                // Update storage pool capacity in database.
                ExternalDeviceUtils.updateStoragePoolCapacityAfterOperationComplete(volume.getPool(), _storageSystemURI,
                        Collections.singletonList(_volumeURI), dbClient);
            } catch (Exception ex) {
                s_logger.error("Failed to update storage pool {} after volume expand operation completion.", volume.getPool(), ex);
            }
        }
    }
}
