/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.driversimulator.operations;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.driversimulator.StorageDriverSimulator;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.task.ExpandVolumeDriverTask;

/**
 * A simulator operation to manage the expansion of a storage volume.
 */
public class ExpandVolumeSimulatorOperation extends BaseDriverSimulatorOperation {
    
    // The name of the operation.
    private static final String OP_NAME = "expand-storage-volumes";
    
    // A reference to a logger.
    private static final Logger _log = LoggerFactory.getLogger(ExpandVolumeSimulatorOperation.class);
    
    /**
     * Constructor.
     * 
     * @param volume A reference to the storage volume being expanded.
     * @param newCapacity The requested new capacity.
     */
    public ExpandVolumeSimulatorOperation(StorageVolume volume, long newCapacity) {
        super(OP_NAME);
        createDriverTask(volume, newCapacity);
    }
    
    /**
     * Update the storage volume information after a successful expansion.
     * 
     * @param volume A reference to the storage volume being expanded.
     * @param newCapacity The requested new capacity.
     */
    public void updateVolumeInfo(StorageVolume volume, long newCapacity) {
        volume.setRequestedCapacity(newCapacity);
        volume.setProvisionedCapacity(newCapacity);
        volume.setAllocatedCapacity(newCapacity);       
    }
    
    @Override
    public void updateOnAsynchronousSuccess() {
        ExpandVolumeDriverTask expandVolumeTask = (ExpandVolumeDriverTask)_task;
        StorageVolume volume = expandVolumeTask.getStorageVolume();
        long newCapacity = expandVolumeTask.getExpandedCapacity();
        updateVolumeInfo(volume, newCapacity);
    }    
    
    @Override
    public String getSuccessMessage(Object... args) {
        StorageVolume volume;
        if ((args != null) && (args.length > 0)) {
            volume = (StorageVolume) args[0];
        } else {
            // Must be asynchronous, so updated volume is in the task.
            ExpandVolumeDriverTask expandVolumeTask = (ExpandVolumeDriverTask)_task;
            volume = expandVolumeTask.getStorageVolume();
        }
        return String.format("StorageDriver: expandVolume information for storage system %s, volume nativeIds %s, new capacity %s - end",
                volume.getStorageSystemId(), volume.toString(), volume.getRequestedCapacity());
    }
    
    @Override
    public String getFailureMessage(Object... args) {
        return "StorageDriver: expandVolume simulated failure";
    }
    
    /**
     * Create the expand volume task that is returned by the request.
     * 
     * @param volume A reference to the storage volume being expanded.
     * @param newCapacity The requested new capacity.
     */
    private void createDriverTask(StorageVolume volume, long newCapacity) {
        String taskId = String.format("%s+%s+%s", StorageDriverSimulator.DRIVER_NAME, OP_NAME, UUID.randomUUID().toString());
        _log.info("Creating task {} for operation of type {}", taskId, OP_NAME);
        _task = new ExpandVolumeDriverTask(taskId, volume, newCapacity);
        _task.setStatus(DriverTask.TaskStatus.PROVISIONING);
    }
}