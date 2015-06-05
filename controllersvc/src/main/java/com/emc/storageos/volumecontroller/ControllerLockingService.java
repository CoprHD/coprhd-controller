/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
