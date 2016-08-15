/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.driversimulator.operations;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.driversimulator.StorageDriverSimulator;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.task.RestoreFromSnapshotDriverTask;

/**
 * A simulator operation to manage a restore from snapshot request.
 */
public class RestoreFromSnapshotSimulatorOperation extends BaseDriverSimulatorOperation {
    
    // The name of the operation.
    private static final String OP_NAME = "restore-snapshot";
    
    // A reference to a logger.
    private static final Logger _log = LoggerFactory.getLogger(RestoreFromSnapshotSimulatorOperation.class);
    
    /**
     * Constructor.
     * 
     * @param snapshots A list of the snapshots to be restored.
     */
    public RestoreFromSnapshotSimulatorOperation(List<VolumeSnapshot> snapshots) {
        super(OP_NAME);
        createDriverTask(snapshots);
    }
    
    @Override
    public void updateOnAsynchronousSuccess() {
        // Nothing to do for restore snapshot.
    }    
    
    @SuppressWarnings("unchecked")
    @Override
    public String getSuccessMessage(Object... args) {
        List<VolumeSnapshot> snapshots;
        if ((args != null) && (args.length > 0)) {
            snapshots = (List<VolumeSnapshot>) args[0];
        } else {
            // Must be asynchronous, so updated snapshots are in the task.
            RestoreFromSnapshotDriverTask restoreSnapshotTask = (RestoreFromSnapshotDriverTask)_task;
            snapshots = restoreSnapshotTask.getSnapshots();
        }
        
        return String.format("StorageDriver: restoreSnapshot for storage system %s, snapshots nativeId %s, snap group %s - end",
                snapshots.get(0).getStorageSystemId(), snapshots.toString(), snapshots.get(0).getConsistencyGroup());
    }
    
    @Override
    public String getFailureMessage(Object... args) {
        return "StorageDriver: restoreSnapshot simulated failure";
    }
        
    /**
     * Create the restore from snapshot task that is returned by the request.
     * 
     * @param snapshots A list of the snapshots to be restored.
     */
    private void createDriverTask(List<VolumeSnapshot> snapshots) {
        String taskId = String.format("%s+%s+%s", StorageDriverSimulator.DRIVER_NAME, OP_NAME, UUID.randomUUID().toString());
        _log.info("Creating task {} for operation of type {}", taskId, OP_NAME);
        _task = new RestoreFromSnapshotDriverTask(taskId, snapshots);
        _task.setStatus(DriverTask.TaskStatus.PROVISIONING);
    }
}