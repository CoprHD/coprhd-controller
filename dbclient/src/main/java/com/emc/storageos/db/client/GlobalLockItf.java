/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client;

/**
 * Interfaces of the Global Lock Across Multiple VDCs
 */
public interface GlobalLockItf {

    /**
     * Acquire the global lock, with a specified lock owner name.
     * If the lock is available, acquire the lock and owner is associated with it.
     * @return true, if lock is acquired
     *         false, otherwise
     */
    boolean acquire(final String owner) throws Exception;

    /**
     * Releases the global lock associated with a specified lock owner name.
     * The lock is released if the specified owner matches with the lock owner
     * @return true, if lock is released
     *         false, otherwise
     */
    boolean release(final String owner) throws Exception;

    /**
     * Get lock owner's name
     * @return the current lock owner name
     *         null, otherwise.
     */
    String getOwner() throws Exception;
}
