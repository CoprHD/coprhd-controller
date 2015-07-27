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
     * Gets a persistent mutex that works across all nodes in the cluster
     * 
     * @param lockName name of lock
     * @param seconds number seconds to try to wait.  0 = check once only, -1 = check forever
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
