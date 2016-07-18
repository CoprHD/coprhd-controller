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
import com.emc.storageos.storagedriver.task.CreateVolumeCloneDriverTask;

/**
 * A simulator operation to manage the creation of a storage volume clone.
 */
public class CreateVolumeCloneSimulatorOperation extends BaseDriverSimulatorOperation {
    
    // The name of the operation.
    private static final String OP_NAME = "create-volume-clone";
    
    // A reference to a logger.
    private static final Logger _log = LoggerFactory.getLogger(CreateVolumeCloneSimulatorOperation.class);
    
    /**
     * Constructor.
     * 
     * @param clones A list of the clones to be created.
     */
    public CreateVolumeCloneSimulatorOperation(List<VolumeClone> clones) {
        super(OP_NAME);
        createDriverTask(clones);
    }
    
    /**
     * Update the clone information after successfully being created.
     * 
     * @param clones A list of the clones to be updated.
     */
    public void updateCloneInfo(List<VolumeClone> clones) {
        for (VolumeClone clone : clones) {
            clone.setNativeId("clone-" + clone.getParentId() + clone.getDisplayName());
            clone.setWwn(String.format("%s%s", clone.getStorageSystemId(), clone.getNativeId()));
            clone.setReplicationState(VolumeClone.ReplicationState.SYNCHRONIZED);
            clone.setProvisionedCapacity(clone.getRequestedCapacity());
            clone.setAllocatedCapacity(clone.getRequestedCapacity());
            clone.setDeviceLabel(clone.getNativeId());
        }        
    }
    
    @Override
    public void updateOnAsynchronousSuccess() {
        CreateVolumeCloneDriverTask createCloneTask = (CreateVolumeCloneDriverTask)_task;
        updateCloneInfo(createCloneTask.getClones());
    }    
    
    @SuppressWarnings("unchecked")
    @Override
    public String getSuccessMessage(Object... args) {
        List<VolumeClone> clones;
        if ((args != null) && (args.length > 0)) {
            clones = (List<VolumeClone>) args[0];
        } else {
            // Must be asynchronous, so updated clones are in the task.
            CreateVolumeCloneDriverTask createCloneTask = (CreateVolumeCloneDriverTask)_task;
            clones = createCloneTask.getClones();
        }
        return String.format("StorageDriver: createVolumeClone information for storage system %s, clone nativeIds %s - end",
                clones.get(0).getStorageSystemId(), clones.toString());
    }
    
    @Override
    public String getFailureMessage(Object... args) {
        return "StorageDriver: createVolumeClone simulated failure";
    }
        
    /**
     * Create the create volume clone task that is returned by the request.
     * 
     * @param clones A list of the clones to be created.
     */
    private void createDriverTask(List<VolumeClone> clones) {
        String taskId = String.format("%s+%s+%s", StorageDriverSimulator.DRIVER_NAME, OP_NAME, UUID.randomUUID().toString());
        _log.info("Creating task {} for operation of type {}", taskId, OP_NAME);
        _task = new CreateVolumeCloneDriverTask(taskId, clones);
        _task.setStatus(DriverTask.TaskStatus.PROVISIONING);
    }
}
