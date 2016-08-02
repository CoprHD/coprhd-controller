/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice.job;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.task.RestoreFromCloneDriverTask;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.ExternalDeviceUtils;

/**
 * This ExternalDeviceJob derived class is created to monitor the progress
 * of a request to restore a group clone that will complete asynchronously.
 */
public class RestoreFromGroupCloneExternalDeviceJob extends ExternalDeviceJob {
    
    private static final long serialVersionUID = 1L;
    
    // The URIs of the volumes representing the controller clones that are being restored.
    private List<URI> _volumeURIs;

    // Logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(RestoreFromGroupCloneExternalDeviceJob.class);

    /**
     * Constructor.
     * 
     * @param storageSystemURI The URI of the external storage system on which the task is running.
     * @param volumeURIs The URIs of the volumes representing the controller clones that are being restored.
     * @param driverTaskId The id of the task monitored by the job.
     * @param taskCompleter The task completer.
     */
    public RestoreFromGroupCloneExternalDeviceJob(URI storageSystemURI, List<URI> volumeURIs, String driverTaskId,
            TaskCompleter taskCompleter) {
        super(storageSystemURI, driverTaskId, taskCompleter);
        _volumeURIs = volumeURIs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doTaskSucceeded(DriverTask driverTask, DbClient dbClient) throws Exception {
        s_logger.info(String.format("Successfully restored group clone: %s", driverTask.getMessage()));

        // Update the ViPR volumes representing the clones with the
        // corresponding driver clone.
        List<Volume> updatedVolumes = new ArrayList<>();
        for (URI volumeURI : _volumeURIs) {
            Volume volume = dbClient.queryObject(Volume.class, volumeURI);
            if (volume == null) {
                s_logger.error(String.format("Failed to find volume %s", volumeURI));
                throw DeviceControllerException.exceptions.objectNotFound(volumeURI);
            }
            
            // Update the ViPR clone with the driver clone information.
            RestoreFromCloneDriverTask restoreDriverTask = (RestoreFromCloneDriverTask) driverTask;
            List<VolumeClone> updatedClones = restoreDriverTask.getClones();
            for (VolumeClone updatedClone: updatedClones) {
                if (ExternalDeviceUtils.isVolumeExternalDeviceClone(volume, updatedClone, dbClient)) {
                    ExternalDeviceUtils.updateRestoredClone(volume, updatedClone, dbClient, false);
                    updatedVolumes.add(volume);
                    break;
                }
            }
        }
        dbClient.updateObject(updatedVolumes);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void doTaskFailed(DriverTask driverTask, DbClient dbClient) throws Exception {
        s_logger.error(String.format("Failed to restore group clone: %s", driverTask.getMessage()));
    }
}

