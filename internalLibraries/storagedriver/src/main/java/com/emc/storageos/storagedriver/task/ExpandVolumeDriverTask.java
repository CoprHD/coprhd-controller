/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.task;

import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageVolume;

/**
 * This DriverTask derived class should be returned when a storage driver request
 * to expand a volume will be completed asynchronously. The storage volume managed and
 * returned by this task should contain the updated storage volume capacities when the
 * task completes successfully.
 */
public class ExpandVolumeDriverTask extends DriverTask {
    
    // A reference to the storage volume associated with the task.
    private StorageVolume _deviceVolume;
    
    // The requested new capacity
    private long _newCapacity;
    
    /**
     * Constructor
     * 
     * @param taskId The unique ID of the task.
     * @param deviceVolume A reference to the storage volume expanded by the task.
     * @param newCapacity The requested new capacity.
     */
    public ExpandVolumeDriverTask(String taskId, StorageVolume deviceVolume, long newCapacity) {
        super(taskId);
        _deviceVolume = deviceVolume;
        _newCapacity = newCapacity;
    }
    
    /**
     * Get the storage volume expanded by the task.
     * 
     * @return The storage volume expanded by the task.
     */
    public StorageVolume getStorageVolume() {
        return _deviceVolume;
    }
    
    /**
     * Get the requested new capacity for the volume.
     * 
     * @return The requested new capacity.
     */
    public long getExpandedCapacity() {
        return _newCapacity;
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