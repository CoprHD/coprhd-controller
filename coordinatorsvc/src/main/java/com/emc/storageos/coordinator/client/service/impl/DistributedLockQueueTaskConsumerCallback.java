package com.emc.storageos.coordinator.client.service.impl;

/**
 * Callback interface for {@link DistributedLockQueueTaskConsumer}.
 *
 * @author Ian Bibby
 */
public interface DistributedLockQueueTaskConsumerCallback {
    void taskConsumed();
}
