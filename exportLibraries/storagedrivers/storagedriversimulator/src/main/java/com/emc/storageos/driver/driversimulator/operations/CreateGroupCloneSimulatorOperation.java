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
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;
import com.emc.storageos.storagedriver.task.CreateGroupCloneDriverTask;

/**
 * A simulator operation to manage the creation of a consistency group clone.
 */
public class CreateGroupCloneSimulatorOperation extends BaseDriverSimulatorOperation {
    
    // The name of the operation.
    private static final String OP_NAME = "expand-storage-volumes";
    
    // A reference to a logger.
    private static final Logger _log = LoggerFactory.getLogger(CreateGroupCloneSimulatorOperation.class);
    
    /**
     * Constructor.
     * 
     * @param consistencyGroup A reference to the consistency group.
     * @param clones A list of the clones to be created.
     */
    public CreateGroupCloneSimulatorOperation(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones) {
        super(OP_NAME);
        createDriverTask(consistencyGroup, clones);
    }
    
    /**
     * Update the clone information after successfully being created.
     * 
     * @param consistencyGroup A reference to the consistency group.
     * @param clones A list of the clones to be updated.
     */
    public void updateGroupCloneInfo(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones) {
        String cloneTimestamp = Long.toString(System.currentTimeMillis());
        for (VolumeClone clone : clones) {
            clone.setNativeId("clone-" + clone.getParentId() + clone.getDisplayName());
            clone.setWwn(String.format("%s%s", clone.getStorageSystemId(), clone.getNativeId()));
            clone.setReplicationState(VolumeClone.ReplicationState.SYNCHRONIZED);
            clone.setProvisionedCapacity(clone.getRequestedCapacity());
            clone.setAllocatedCapacity(clone.getRequestedCapacity());
            clone.setDeviceLabel(clone.getNativeId());
            clone.setConsistencyGroup(consistencyGroup.getNativeId() + "_clone-" + cloneTimestamp);
        }        
    }
    
    @Override
    public void updateOnAsynchronousSuccess() {
        CreateGroupCloneDriverTask createCloneTask = (CreateGroupCloneDriverTask)_task;
        List<VolumeClone> clones = createCloneTask.getClones();
        VolumeConsistencyGroup consistencyGroup = createCloneTask.getConsistencyGroup();
        updateGroupCloneInfo(consistencyGroup, clones);
    }    
    
    @SuppressWarnings("unchecked")
    @Override
    public String getSuccessMessage(Object... args) {
        List<VolumeClone> clones;
        if ((args != null) && (args.length > 0)) {
            clones = (List<VolumeClone>) args[0];
        } else {
            // Must be asynchronous, so updated clones are in the task.
            CreateGroupCloneDriverTask createCloneTask = (CreateGroupCloneDriverTask)_task;
            clones = createCloneTask.getClones();
        }
        return String.format("StorageDriver: createGroupClone information for group %s on storage system %s, clones nativeIds %s - end",
                clones.get(0).getConsistencyGroup(), clones.get(0).getStorageSystemId(), clones.toString());
    }
    
    @Override
    public String getFailureMessage(Object... args) {
        return "StorageDriver: createGroupClone simulated failure";
    }
        
    /**
     * Create the create consistency group clone task that is returned by the request.
     * 
     * @param consistencyGroup A reference to the consistency group.
     * @param clones A list of the clones to be created.
     */
    private void createDriverTask(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones) {
        String taskId = String.format("%s+%s+%s", StorageDriverSimulator.DRIVER_NAME, OP_NAME, UUID.randomUUID().toString());
        _log.info("Creating task {} for operation of type {}", taskId, OP_NAME);
        _task = new CreateGroupCloneDriverTask(taskId, consistencyGroup, clones);
        _task.setStatus(DriverTask.TaskStatus.PROVISIONING);
    }
}
