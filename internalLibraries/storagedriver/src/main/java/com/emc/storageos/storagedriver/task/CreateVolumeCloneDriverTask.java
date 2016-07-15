/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.task;

import java.util.List;

import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.VolumeClone;

/**
 * This DriverTask derived class should be returned when a storage driver request
 * to create a volume clone will be completed asynchronously. The clones managed and
 * returned by this task should contain the updated clone data when the task
 * completes successfully.
 */
public class CreateVolumeCloneDriverTask extends DriverTask {
    
    // A reference to the clones associated with the task.
    private  List<VolumeClone> _volumeClones;
    
    /**
     * Constructor
     * 
     * @param taskId The unique ID of the task.
     * @param volumeClones The clones to be created by the task.
     */
    public CreateVolumeCloneDriverTask(String taskId, List<VolumeClone> volumeClones) {
        super(taskId);
        _volumeClones = volumeClones;
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
