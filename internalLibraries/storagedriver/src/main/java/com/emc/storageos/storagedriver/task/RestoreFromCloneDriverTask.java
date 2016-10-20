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
 * to restore from clone will be completed asynchronously. The clones managed and
 * returned by this task should contain the updated clone data when the task
 * completes successfully.
 */
public class RestoreFromCloneDriverTask extends DriverTask {
    
    // A reference to the clones associated with the task.
    private  List<VolumeClone> _volumeClones;
    
    /**
     * Constructor
     * 
     * @param taskId The unique ID of the task.
     * @param volumeClones The clones to be restored by the task.
     */
    public RestoreFromCloneDriverTask(String taskId, List<VolumeClone> volumeClones) {
        super(taskId);
        _volumeClones = volumeClones;
    }
    
    /**
     * Get the clones restored by the task.
     * 
     * @return The clones restored by the task.
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
