/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller;

import com.emc.storageos.coordinator.client.service.WorkPool;
import com.emc.storageos.db.client.model.StorageSystem;

/**
 * Defines the interface classes that monitor storage devices for events and
 * alerts.
 */
public interface StorageMonitor {

    /**
     * Monitor the passed storage device for events.
     * 
     * @param storageDevice A reference to the storage device.
     */
    public void startMonitoring(StorageSystem storageDevice, WorkPool.Work work) throws StorageMonitorException;

    /**
     * Stop monitoring the passed storage device for events.
     * 
     * @param storageDevice A reference to the storage device.
     */
    public void stopMonitoring(StorageSystem storageDevice) throws StorageMonitorException;
    
    /**
     * Shuts down the storage monitor so that event monitoring is stopped for
     * all storage devices being monitored and all resources are cleaned up.
     */
    public void shutdown();
}
