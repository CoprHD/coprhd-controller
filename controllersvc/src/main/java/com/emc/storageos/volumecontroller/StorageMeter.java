/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import com.emc.storageos.db.client.model.StorageSystem;

/**
 * Defines the interface classes that collect statistics for storage devices.
 */
public interface StorageMeter {

    /**
     * Metering the passed storage device for statistics collection.
     * 
     * @param storageDevice A reference to the storage device.
     */
    public void startMeteringDevice(StorageSystem storageDevice) throws StorageMeteringException;

    /**
     * Stop monitoring the passed storage device for events.
     * 
     * @param storageDevice A reference to the storage device.
     */
    public void stopMeteringDevice(StorageSystem storageDevice) throws StorageMeteringException;
    /**
     * Shutdown the metering scheduler when controller is shutdown.
     */
    public void shutdown();
}
