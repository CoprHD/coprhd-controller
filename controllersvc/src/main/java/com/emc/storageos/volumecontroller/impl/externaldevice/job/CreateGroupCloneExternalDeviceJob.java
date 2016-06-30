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
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.DriverTask.TaskStatus;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.task.CreateVolumeCloneDriverTask;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.ExternalDeviceUtils;

/**
 * This ExternalDeviceJob derived class is created to monitor the progress
 * of a request to a create group clone that will complete asynchronously.
 */
public class CreateGroupCloneExternalDeviceJob extends ExternalDeviceJob {
    
    private static final long serialVersionUID = 1L;
    
    // The URI of the volume serving as the clone.
    private List<URI> _volumeURIs;
    
    // The URI of the consistency group
    private URI _cgURI;

    // Logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(CreateGroupCloneExternalDeviceJob.class);

    /**
     * Constructor.
     * 
     * @param storageSystemURI The URI of the external storage system on which the task is running.
     * @param volumeURIs The URI of the volume serving as the clone.
     * @param cgURI The consistency group URI.
     * @param driverTaskId The id of the task monitored by the job.
     * @param taskCompleter The task completer.
     */
    public CreateGroupCloneExternalDeviceJob(URI storageSystemURI, List<URI> volumeURIs, URI cgURI,
            String driverTaskId, TaskCompleter taskCompleter) {
        super(storageSystemURI, driverTaskId, taskCompleter);
        _volumeURIs = volumeURIs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doTaskSucceeded(DriverTask driverTask, DbClient dbClient) throws Exception {
        s_logger.info(String.format("Successfully created group clone:%s.", driverTask.getMessage()));
        
        // Update the ViPR volumes representing the clone with the
        // corresponding driver clone.
        List<Volume> updatedVolumes = new ArrayList<>();
        for (URI volumeURI : _volumeURIs) {
            Volume volume = dbClient.queryObject(Volume.class, volumeURI);
            if (volume == null) {
                s_logger.error(String.format("Failed to find volume %s", volumeURI));
                // Exception?
            }
            
            // We need the associated source volume for the ViPR clone
            // volume to determine which driver clone maps to the ViPR
            // clone.
            URI assocSourceVolumeURI = volume.getAssociatedSourceVolume();
            Volume assocSourceVolume = dbClient.queryObject(Volume.class, assocSourceVolumeURI);
            String assocSourceVolumeNativeId = assocSourceVolume.getNativeId();
            
            // Update the ViPR clone with the driver clone information.
            CreateVolumeCloneDriverTask createCloneDriverTask = (CreateVolumeCloneDriverTask) driverTask;
            List<VolumeClone> updatedClones = createCloneDriverTask.getClones();
            for (VolumeClone updatedClone: updatedClones) {
                if (updatedClone.getParentId().equals(assocSourceVolumeNativeId)) {
                    ExternalDeviceUtils.updateGroupVolumeFromClone(volume, updatedClone, _cgURI, dbClient);
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
        s_logger.error(String.format("Failed to create group volume clone: %s", driverTask.getMessage()));
        
        List<Volume> volumes = new ArrayList<>();
        for (URI volumeURI : _volumeURIs) {
            Volume volume = dbClient.queryObject(Volume.class, volumeURI);
            if (volume == null) {
                s_logger.error(String.format("Failed to find volume %s", volumeURI));
                // Exception?
            } else {
                volume.setInactive(true);
                volumes.add(volume);
            }
        }
        dbClient.updateObject(volumes);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isTaskSuccessful(TaskStatus taskStatus) {
        // Since clones are created one at a time, the task is successful
        // only it is is TaskStatus.READY.
        return (TaskStatus.READY == taskStatus);
    }
}