/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.task;

import java.util.List;

import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;

/**
 * This DriverTask derived class should be returned when a storage driver request
 * to create a group clone will be completed asynchronously. The clones managed and
 * returned by this task should contain the updated clone data when the task
 * completes successfully.
 */
public class CreateGroupCloneDriverTask extends DriverTask {
    
    // A reference to the volume consistency group.
    private VolumeConsistencyGroup _consistencyGroup;
    
    // A reference to the clones associated with the task.
    private  List<VolumeClone> _volumeClones;
    
    /**
     * Constructor
     * 
     * @param taskId The unique ID of the task.
     * @param consistencyGroup A reference to the volume consistency group.
     * @param volumeClones The clones to be created by the task.
     */
    public CreateGroupCloneDriverTask(String taskId, VolumeConsistencyGroup consistencyGroup, List<VolumeClone> volumeClones) {
        super(taskId);
        _consistencyGroup = consistencyGroup;
        _volumeClones = volumeClones;
    }
    
    /**
     * Get the consistency group.
     * 
     * @return The consistency group.
     */
    public VolumeConsistencyGroup getConsistencyGroup() {
        return _consistencyGroup;
    }
    
    /**
     * Get the clones created by the task.
     * 
     * @return The clones created by the task.
     */
    public List<VolumeClone> getClones() {
        return _volumeClones;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DriverTask abort(DriverTask task) {
        // TODO Auto-generated method stub
        return null;
    }
}