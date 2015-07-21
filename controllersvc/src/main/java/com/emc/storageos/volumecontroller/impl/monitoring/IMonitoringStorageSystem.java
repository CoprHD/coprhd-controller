/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.monitoring;

import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;

public interface IMonitoringStorageSystem {
    
    /**
     * Starts monitoring for the given monitoringJob token.
     * @param monitoringJob {@link MonitoringJob} monitoringToken available from zoo keeper queue
     * @param callback {@link DistributedQueueItemProcessedCallback} callback instance
     */
    public void startMonitoring(MonitoringJob monitoringJob, DistributedQueueItemProcessedCallback callback);
    
    /**
     * Scheduled activity for the acquired monitoring job's token.
     */
    public void scheduledMonitoring();
    
    /**
     * Stops monitoring for the stale StorageDevices
     */
    public void stopMonitoringStaleSystem();
    
    /**
     * Clears local CACHE while zk reconnect
     */
    public void clearCache();
}
