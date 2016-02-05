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
     * @param timeout  time to wait for lock
     * @param unit     unit for time
     * @return
     */
    public boolean acquireLock(String lockName, long timeout, TimeUnit unit);

    /**
     * Releases lock.
     * @param lockName
     */
    public void releaseLock(String lockName);

}

