/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.task;

import java.util.List;

import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;

/**
 * This DriverTask derived class should be returned when a storage driver request
 * to restore from snapshot will be completed asynchronously. The snapshots managed and
 * returned by this task should contain any updated snapshot data when the task
 * completes successfully.
 */
public class RestoreFromSnapshotDriverTask extends DriverTask {
    
    // A reference to the snapshots associated with the task.
    private  List<VolumeSnapshot> _volumeSnapshots;
    
    /**
     * Constructor
     * 
     * @param taskId The unique ID of the task.
     * @param volumeSnapshots The snapshots to be restored by the task.
     */
    public RestoreFromSnapshotDriverTask(String taskId, List<VolumeSnapshot> volumeSnapshots) {
        super(taskId);
        _volumeSnapshots = volumeSnapshots;
    }
    
    /**
     * Get the snapshots restored by the task.
     * 
     * @return The snapshots restored by the task.
     */
    public List<VolumeSnapshot> getSnapshots() {
        return _volumeSnapshots;
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