/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service;

import java.util.List;

/**
 * Main API for coordinator backed distributed queue
 */
public interface DistributedQueue<T> {
    /**
     * Starts distributed queue. If coordinator cluster is unavailable, start will retry
     * until it can establish connection with cluster. Attempting to put items into queue
     * will result in error during this time.
     */
    public void start();

    /**
     * Stops distributed queue. Queue will stop retrieving new items from queue. Pending
     * tasks continue to process until wait queue is drained / fully processed. Blocks for
     * given wait time in ms.
     * 
     * @return true if all pending items have been processed. false, otherwise.
     */
    public boolean stop(long timeoutMs);

    /**
     * Puts an item into distributed queue
     * 
     * @param item
     */
    public void put(T item) throws Exception;
    
    /**
     * get the list of queued but not active items for this queue
     * 
     * @return
     */
    public List<T> getQueuedItems();
    
    /**
     * get the list of active items for this queue
     * 
     * @return
     */
    public List<T> getActiveItems();
    
}
