package com.emc.storageos.coordinator.client.service;

import com.emc.storageos.coordinator.client.service.impl.DistributedLockQueueManagerImpl.Event;

/**
 * Interface for listening to {@link DistributedLockQueueManager} events.
 *
 * @author Ian Bibby
 */
public interface DistributedLockQueueEventListener<T> {
    /**
     * Event handler method.
     *
     * @param task  The task
     * @param event The event
     */
    void lockQueueEvent(T task, Event event);
}
