/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
