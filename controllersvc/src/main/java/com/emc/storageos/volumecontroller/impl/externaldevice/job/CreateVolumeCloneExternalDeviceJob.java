/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice.job;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.task.CreateVolumeCloneDriverTask;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.ExternalBlockStorageDevice;
import com.emc.storageos.volumecontroller.impl.externaldevice.ExternalDeviceUtils;

/**
 * This ExternalDeviceJob derived class is created to monitor the progress
 * of a request to a create volume clone that will complete asynchronously.
 */
public class CreateVolumeCloneExternalDeviceJob extends ExternalDeviceJob {
    
    private static final long serialVersionUID = 1L;
    
    // The URI of the volume serving as the clone.
    private URI _volumeURI;

    // Logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(CreateVolumeCloneExternalDeviceJob.class);

    /**
     * Constructor.
     * 
     * @param storageSystemURI The URI of the external storage system on which the task is running.
     * @param volumeURI The URI of the volume serving as the clone.
     * @param driverTaskId The id of the task monitored by the job.
     * @param taskCompleter The task completer.
     */
    public CreateVolumeCloneExternalDeviceJob(URI storageSystemURI, URI volumeURI, String driverTaskId,
            TaskCompleter taskCompleter) {
        super(storageSystemURI, driverTaskId, taskCompleter);
        _volumeURI = volumeURI;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doTaskSucceeded(DriverTask driverTask, DbClient dbClient) throws Exception {
        // Get the ViPR volume representing the clone.
        s_logger.info(String.format("Successfully created volume clone %s:%s", _volumeURI, driverTask.getMessage()));
        Volume volume = dbClient.queryObject(Volume.class, _volumeURI);
        if (volume == null) {
            s_logger.error(String.format("Failed to find volume %s", _volumeURI));
            throw DeviceControllerException.exceptions.objectNotFound(_volumeURI);
        }
        
        // Update the ViPR clone with the driver clone information.
        // Note that we know ViPR only allows creation of a single
        // in a given request.
        CreateVolumeCloneDriverTask createCloneDriverTask = (CreateVolumeCloneDriverTask) driverTask;
        List<VolumeClone> updatedClones = createCloneDriverTask.getClones();
        VolumeClone updatedClone = updatedClones.get(0);
        ExternalDeviceUtils.updateNewlyCreatedClone(volume, updatedClone, dbClient);

        try {
            // Update storage pool capacity in database.
            ExternalDeviceUtils.updateStoragePoolCapacityAfterOperationComplete(volume.getPool(), _storageSystemURI,
                    Collections.singletonList(_volumeURI), dbClient);
        } catch (Exception ex) {
            s_logger.error("Failed to update storage pool {} after create clone operation completion.", volume.getPool(), ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doTaskFailed(DriverTask driverTask, DbClient dbClient) throws Exception {
        s_logger.error(String.format("Failed to create volume clone %s:%s", _volumeURI, driverTask.getMessage()));
        Volume volume = dbClient.queryObject(Volume.class, _volumeURI);
        if (volume == null) {
            s_logger.error(String.format("Failed to find volume %s", _volumeURI));
        } else {
            volume.setInactive(true);
            dbClient.updateObject(volume);

            try {
                // Update storage pool capacity in database.
                ExternalDeviceUtils.updateStoragePoolCapacityAfterOperationComplete(volume.getPool(), _storageSystemURI,
                        Collections.singletonList(_volumeURI), dbClient);
            } catch (Exception ex) {
                s_logger.error("Failed to update storage pool {} after create clone operation completion.", volume.getPool(), ex);
            }
        }
    }
}