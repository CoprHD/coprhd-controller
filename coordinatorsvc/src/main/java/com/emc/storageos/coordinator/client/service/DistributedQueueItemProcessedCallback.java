/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service;

/**
 * QueueItemProcessedCallback
 */
public interface DistributedQueueItemProcessedCallback {

    /**
     * Removes an item from the associated distributed queue.
     * This method must be called by DistributedQueueConsumer's after successfully consuming an item.
     * 
     * @throws Exception
     */
    public void itemProcessed() throws Exception;
}