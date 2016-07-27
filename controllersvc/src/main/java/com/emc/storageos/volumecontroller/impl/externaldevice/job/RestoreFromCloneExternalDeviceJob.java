/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice.job;

import java.net.URI;
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
 * of a request to restore a volume from a clone that will complete asynchronously.
 */
public class RestoreFromCloneExternalDeviceJob extends ExternalDeviceJob {
    
    private static final long serialVersionUID = 1L;
    
    // The URI of the volume representing the controller clone that is being restored.
    private URI _volumeURI;

    // Logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(RestoreFromCloneExternalDeviceJob.class);

    /**
     * Constructor.
     * 
     * @param storageSystemURI The URI of the external storage system on which the task is running.
     * @param volumeURI The URI of the volume representing the controller clone that is being restored.
     * @param driverTaskId The id of the task monitored by the job.
     * @param taskCompleter The task completer.
     */
    public RestoreFromCloneExternalDeviceJob(URI storageSystemURI, URI volumeURI, String driverTaskId,
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
        s_logger.info(String.format("Successfully restored clone %s:%s", _volumeURI, driverTask.getMessage()));
        Volume volume = dbClient.queryObject(Volume.class, _volumeURI);
        if (volume == null) {
            s_logger.error(String.format("Failed to find volume %s", _volumeURI));
            throw DeviceControllerException.exceptions.objectNotFound(_volumeURI);
        }
        
        // Update the ViPR clone with the driver clone information.
        // Note that we know ViPR only allows you to restore a single
        // non-group clone at a time.
        RestoreFromCloneDriverTask restoreDriverTask = (RestoreFromCloneDriverTask) driverTask;
        List<VolumeClone> updatedClones = restoreDriverTask.getClones();
        VolumeClone updatedClone = updatedClones.get(0);
        ExternalDeviceUtils.updateRestoredClone(volume, updatedClone, dbClient, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doTaskFailed(DriverTask driverTask, DbClient dbClient) throws Exception {
        s_logger.error(String.format("Failed to restore from clone %s:%s", _volumeURI, driverTask.getMessage()));
    }
}
