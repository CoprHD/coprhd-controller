/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice.job;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * This ExternalDeviceJob derived class is created to monitor the progress
 * of a request to restore a volume from a snapshot that will complete asynchronously.
 * The job is used when restoring both single volume snapshots and also group snapshots.
 */
public class RestoreFromSnapshotExternalDeviceJob extends ExternalDeviceJob {
    
    private static final long serialVersionUID = 1L;
    
    // The URI of the controller snapshot being restored.
    private URI _snapshotURI;

    // Logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(RestoreFromSnapshotExternalDeviceJob.class);

    /**
     * Constructor.
     * 
     * @param storageSystemURI The URI of the external storage system on which the task is running.
     * @param snapshotURI The URI of the controller snapshot being restored.
     * @param driverTaskId The id of the task monitored by the job.
     * @param taskCompleter The task completer.
     */
    public RestoreFromSnapshotExternalDeviceJob(URI storageSystemURI, URI snapshotURI, String driverTaskId,
            TaskCompleter taskCompleter) {
        super(storageSystemURI, driverTaskId, taskCompleter);
        _snapshotURI = snapshotURI;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doTaskSucceeded(DriverTask driverTask, DbClient dbClient) throws Exception {
        // Get the ViPR volume representing the clone.
        s_logger.info(String.format("Successfully restored snapshot %s:%s", _snapshotURI, driverTask.getMessage()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doTaskFailed(DriverTask driverTask, DbClient dbClient) throws Exception {
        s_logger.error(String.format("Failed to restore snapshot %s:%s", _snapshotURI, driverTask.getMessage()));
    }
}

