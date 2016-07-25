/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver;

import java.util.concurrent.TimeUnit;


public interface LockManager {

    /**
     * Acquire lock. Blocks until lock is available or timeout is reached.
     * If timeout is 0, returns immediately
     *
     * @param lockName unique lock name
     * @param timeout time to try to wait for lock. 0 - return immediately, -1 = wait forever
     * @param unit timeout unit
     *
     * @return true if lock is acquired, false otherwise
     */
    public boolean acquireLock(String lockName, long timeout, TimeUnit unit);

    /**
     * Releases lock.
     * @param lockName lock name
     * @return true if lock is released, false otherwise
     */
    public boolean releaseLock(String lockName);

}

