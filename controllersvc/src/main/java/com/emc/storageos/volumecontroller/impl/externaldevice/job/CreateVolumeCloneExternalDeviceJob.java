/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice.job;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

/**
 * 
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
    protected void doTaskSucceeded(DriverTask driverTask, DbClient dbClient) {
        // Get the ViPR volume representing the clone.
        s_logger.info(String.format("Successfully created volume clone %s:%s.", _volumeURI, driverTask.getMessage()));
        Volume volume = dbClient.queryObject(Volume.class, _volumeURI);
        if (volume == null) {
            s_logger.error(String.format("Failed to find volume %s", _volumeURI));
        }
        
        // Update the ViPR clone with the driver clone information.
        try {
            VolumeClone driverCloneResult = null;//driverTask.getTaskData();
            volume.setNativeId(driverCloneResult.getNativeId());
            volume.setWWN(driverCloneResult.getWwn());
            volume.setDeviceLabel(driverCloneResult.getDeviceLabel());
            volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, volume));
            volume.setReplicaState(driverCloneResult.getReplicationState().name());
            volume.setProvisionedCapacity(driverCloneResult.getProvisionedCapacity());
            volume.setAllocatedCapacity(driverCloneResult.getAllocatedCapacity());
            volume.setInactive(false);
            dbClient.updateObject(volume);          
        } catch (Exception e) {
            // handle this
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doTaskFailed(DriverTask driverTask, DbClient dbClient) {
        s_logger.error(String.format("Failed to create volume clone %s:%s", _volumeURI, driverTask.getMessage()));
        Volume volume = dbClient.queryObject(Volume.class, _volumeURI);
        if (volume == null) {
            s_logger.error(String.format("Failed to find volume %s", _volumeURI));
        } else {
            volume.setInactive(true);
            dbClient.updateObject(volume);
        }
    }
}