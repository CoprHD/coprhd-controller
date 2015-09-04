package com.emc.storageos.coordinator.client.service;

import com.emc.storageos.coordinator.client.service.impl.DistributedLockQueueItemNameGenerator;

import java.util.List;
import java.util.Set;

/**
 * An interface for a distributed lock queue manager service, which provides basic queue operations for items against
 * an arbitrary queue.  For example:
 *
 * queue-1: [item-1, item-2, ...]
 * queue-2: [item-3, ...]
 *
 * See {@link com.emc.storageos.coordinator.client.service.impl.DistributedLockQueueManagerImpl}.
 *
 * @author Ian Bibby
 */
public interface DistributedLockQueueManager<T> {
    /**
     * Start the service.
     */
    void start();

    /**
     * Stop the service.
     */
    void stop();

    /**
     * Add item to the queue represented by the value of lockKey.
     *
     * @param lockKey   Name of the queue.
     * @param item      Item to be added.
     * @return          true, if the item was successfully added.
     */
    boolean queue(String lockKey, T item);

    /**
     * Remove an item from the queue represented by the value of lockKey.
     *
     * @param lockKey   Name of the queue.
     * @return          true, if an item was removed.
     */
    boolean dequeue(String lockKey);

    /**
     * Return a modifiable list of {@link DistributedLockQueueEventListener}.
     *
     * @return  List of {@link DistributedLockQueueEventListener}
     */
    List<DistributedLockQueueEventListener<T>> getListeners();

    /**
     * Return all currently available lock keys that represent queues of items.
     *
     * @return  Set of lock keys.
     */
    Set<String> getLockKeys();

    /**
     * Removes a lock key, representing a queue of items.
     *
     * @param lockKey   Name of a queue.
     */
    void removeLockKey(String lockKey);

    /**
     * Set the name generator which deals with how to name items in a queue.
     *
     * @param nameGenerator The item name generator.
     */
    void setNameGenerator(DistributedLockQueueItemNameGenerator<T> nameGenerator);
}
