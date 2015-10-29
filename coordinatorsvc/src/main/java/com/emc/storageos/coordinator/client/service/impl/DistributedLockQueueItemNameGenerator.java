package com.emc.storageos.coordinator.client.service.impl;

/**
 * Used by {@link DistributedLockQueueManagerImpl} to generate names for each item placed in the queue.
 *
 * @author Ian Bibby
 */
public interface DistributedLockQueueItemNameGenerator<T> {
    String generate(T item);
}
