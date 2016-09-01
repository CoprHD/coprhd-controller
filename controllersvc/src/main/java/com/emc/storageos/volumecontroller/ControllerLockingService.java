/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

/**
 * Controller locking service that provides a block and timeout feature. The following are 
 * some behaviors of the lock
 * 1) Fully distributed locks across multiple JVMs and hosts. It's globally synchronous, meaning 
 *    at any snapshot in time no two clients think they hold the same lock.
 * 2) Non re-entrant. If a owner has acquired the lock, subsequent calls to acquireLock returns false  
 * 3) Persistent. It survives client reboots. After it is acquired and the owner dies, the lock is 
 *    still held and no one else could get the lock anymore. It need be explicitly released.  
 */
public interface ControllerLockingService {

    /**
     * Gets a persistent mutex that works across all nodes in the cluster
     * 
     * @param lockName name of lock
     * @param seconds number seconds to try to wait. 0 = check once only, -1 = check forever
     * @return true if lock is acquired, false otherwise
     */
    public boolean acquireLock(String lockName, long seconds);

    /**
     * Releases a persistent mutex
     * 
     * @param lockName name of lock
     * @return true if lock is released, false otherwise
     */
    public boolean releaseLock(String lockName);
}
