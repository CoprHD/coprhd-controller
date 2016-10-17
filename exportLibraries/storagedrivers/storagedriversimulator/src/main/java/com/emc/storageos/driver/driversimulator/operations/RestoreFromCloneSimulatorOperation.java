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
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.task.RestoreFromCloneDriverTask;

/**
 * A simulator operation to manage a restore from clone request.
 */
public class RestoreFromCloneSimulatorOperation extends BaseDriverSimulatorOperation {
    
    // The name of the operation.
    private static final String OP_NAME = "restore-volume-clones";
    
    // A reference to a logger.
    private static final Logger _log = LoggerFactory.getLogger(RestoreFromCloneSimulatorOperation.class);
    
    /**
     * Constructor.
     * 
     * @param clones A list of the clones to be restored.
     */
    public RestoreFromCloneSimulatorOperation(List<VolumeClone> clones) {
        super(OP_NAME);
        createDriverTask(clones);
    }
    
    /**
     * Update the clone information after successfully being restored.
     * 
     * @param clones A list of the clones to be updated.
     */
    public void updateCloneInfo(List<VolumeClone> clones) {
        for (VolumeClone clone : clones) {
            clone.setReplicationState(VolumeClone.ReplicationState.RESTORED);
        }        
    }
    
    @Override
    public void updateOnAsynchronousSuccess() {
        RestoreFromCloneDriverTask restoreCloneTask = (RestoreFromCloneDriverTask)_task;
        updateCloneInfo(restoreCloneTask.getClones());
    }    
    
    @SuppressWarnings("unchecked")
    @Override
    public String getSuccessMessage(Object... args) {
        List<VolumeClone> clones;
        if ((args != null) && (args.length > 0)) {
            clones = (List<VolumeClone>) args[0];
        } else {
            // Must be asynchronous, so updated clones are in the task.
            RestoreFromCloneDriverTask restoreCloneTask = (RestoreFromCloneDriverTask)_task;
            clones = restoreCloneTask.getClones();
        }
        return String.format("StorageDriver: restoreFromClone : clones %s", clones.toString());
    }
    
    @Override
    public String getFailureMessage(Object... args) {
        return "StorageDriver: restoreFromClone simulated failure";
    }
        
    /**
     * Create the restore from clone task that is returned by the request.
     * 
     * @param clones A list of the clones to be restored.
     */
    private void createDriverTask(List<VolumeClone> clones) {
        String taskId = String.format("%s+%s+%s", StorageDriverSimulator.DRIVER_NAME, OP_NAME, UUID.randomUUID().toString());
        _log.info("Creating task {} for operation of type {}", taskId, OP_NAME);
        _task = new RestoreFromCloneDriverTask(taskId, clones);
        _task.setStatus(DriverTask.TaskStatus.PROVISIONING);
    }
}