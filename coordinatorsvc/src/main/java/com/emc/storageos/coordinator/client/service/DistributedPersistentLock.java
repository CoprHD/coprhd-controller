/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service;

/**
 * Main API for coordinator backed distributed persistent lock (survives disconnects)
 */
public interface DistributedPersistentLock {

    /**
     * Starts the distributed persistent lock.
     */
    public void start() throws Exception;

    /**
     * Stops the distributed persistent lock.
     */
    public void stop();

    /**
     * Non-blocking method to acquire the persistent lock, with a specified clientName (lock owner) name.
     * If the lock is available,
     *   the lock is granted,
     *   the current requester name is established as lock owner.
     * Exception is thrown, if null clientName is specified or something is wrong per ZK.
     * Client is encouraged to catch and retry.
     * @return true, if lock is granted
     *         false, otherwise
     */
    public boolean acquireLock(final String clientName) throws Exception;

    /**
     * Releases the persistent lock associated with a specified client name.
     * The lock is released only if the specified clientName matches lock owner information.
     * Exception is thrown, if null clientName is specified or something is wrong per ZK.
     * Client is encouraged to catch and retry.
     * @return true, if lock is released
     *         false, otherwise
     */
    public boolean releaseLock(final String clientName) throws Exception;

    /**
     * Convenience method to determine the current owner of the persistent lock.
     * Exception is thrown if the request failed for any reason.
     * Client is encouraged to catch and retry.
     * @return lock owner name, if lock is held
     *         null, otherwise.
     */
    public String getLockOwner() throws Exception;
}
