/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service;

import java.util.concurrent.TimeUnit;
import org.apache.curator.framework.recipes.locks.Lease;

/**
 * Main API for coordinator backed distributed semaphore
 */
public interface DistributedSemaphore {

    /**
     * Starts the distributed semaphore.
     */
    public void start();

    /**
     * Stops the distributed semaphore.
     */
    public void stop();

    /**
     * P operation of the semaphore. Blocks if no permit is available.
     *
     * @return Lease
     */
    public Lease acquireLease() throws Exception;

    /**
     * P operation of the semaphore. Blocks if no permit is available until specified time limit is exceeded.
     * If the specified time limit is exceeded, returns null.
     *
     * @param waitTime The amount of time to wait
     * @param waitTimeUnit The unit of waitTime
     *
     * @return Lease:
     *         valid, if the semaphore is acquired within the specified time limit
     *         null, otherwise.
     */
    public Lease acquireLease(long waitTime, TimeUnit waitTimeUnit) throws Exception;

    /**
     * V operation of the semaphore.
     * This method <b>must</b> be called by clients who called acquireLease methods to release
     * the lease, preferably in a finally block.
     * Leases are freed by the ZK server, in the event of ZK client session drops.
     *
     * @param lease
     */
    public void returnLease(Lease lease) throws Exception;
}
