/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

/**
 * Controller locking service that provides a block and timeout feature.
 */
public interface ControllerLockingService {

    /**
     * Gets a distributed lock that works across all nodes in the cluster. 
     * It is globally synchronous, meaning at any snapshot in time no two clients think they hold the same lock.
     * The lock should be acquired/released in same thread. If the ower dies, the lock is released automatically
     * 
     * @param lockName name of lock
     * @param seconds number seconds to try to wait. 0 = check once only, -1 = check forever
     * @return true if lock is acquired, false otherwise
     */
    public boolean acquireLock(String lockName, long seconds);

    /**
     * Releases a distributed lock acquired by {@link #acquireLock(String, long)}
     * 
     * @param lockName name of lock
     */
    public boolean releaseLock(String lockName);
    
    /**
     * Gets a persistent lock that works across all nodes in the cluster. 
     * It could be acquired in one thread, and released in another thread. The clientName should be same
     * If the owner dies, the lock is still held until explicitly released by {@link #releasePersistentLock(String, String)}
     * If it is acquired twice by same owner, it returns true.
     * 
     * @param lockName name of lock
     * @param clientName client name
     * @param seconds number seconds to try to wait. 0 = check once only, -1 = check forever
     * @return true if lock is acquired, false otherwise
     */
    public boolean acquirePersistentLock(String lockName, String clientName, long seconds);

    /**
     * Releases a persistent lock acquired by {@link #acquirePersistentLock(String, String, long)}
     * 
     * @param lockName name of lock
     * @param clientName client name
     * @return true if lock is released, false otherwise
     */
    public boolean releasePersistentLock(String lockName, String clientName);
}
