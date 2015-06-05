/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
