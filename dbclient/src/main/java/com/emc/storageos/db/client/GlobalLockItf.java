/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client;

/**
 * Interfaces of the Global Lock Across Multiple VDCs
 */
public interface GlobalLockItf {

    /**
     * Acquire the global lock, with a specified lock owner name.
     * If the lock is available, acquire the lock and owner is associated with it.
     * 
     * @return true, if lock is acquired
     *         false, otherwise
     */
    public boolean acquire(final String owner) throws Exception;

    /**
     * Releases the global lock associated with a specified lock owner name.
     * The lock is released if the specified owner matches with the lock owner
     * 
     * @return true, if lock is released
     *         false, otherwise
     */
    public boolean release(final String owner) throws Exception;

    /**
     * Get lock owner's name
     * 
     * @return the current lock owner name
     *         null, otherwise.
     */
    public String getOwner() throws Exception;
}
