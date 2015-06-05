/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vplexcontroller;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.recipes.locks.InterProcessLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;

/**
 * A lock manager for locking calls into the VPLEX API that can have concurrency issues.
 */
public class VPlexApiLockManager {
    // Lock name delimiter.
    private static final String LOCK_NAME_DELIM = ":";
       
    // A reference to the coordinator. 
    private CoordinatorClient _coordinator;
    
    // A map of the acquired locks.
    private static ConcurrentHashMap<String, InterProcessLock> s_acquiredLocks = new ConcurrentHashMap<String, InterProcessLock>();

    // A reference to the logger.
    private static final Logger s_logger = LoggerFactory.getLogger(VPlexApiLockManager.class);

    /**
     * Setter for injecting the coordinator.
     * 
     * @param coordinator A reference to the coordinator.
     */
    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    /**
     * Attempts to acquire the passed lock.
     * 
     * @param lockName The name of the lock to acquire.
     * @param waitInSeconds The amount of time to wait to acquire the lock in
     *        seconds. A value less than 0 will cause the function
     *        to wait indefinitely for the lock.
     * 
     * @return true if lock acquired, false otherwise.
     */
    public boolean acquireLock(String lockName, long waitInSeconds) {
        
        if (lockName == null || lockName.isEmpty()) {
            s_logger.info("No lock name specified.");
            return false;
        }
        
        try {
            InterProcessLock lock = _coordinator.getLock(lockName);
            if (lock != null) {
                if (waitInSeconds >= 0) {
                    s_logger.info("Attempting to acquire lock: " + lockName + " for a maximum of " + waitInSeconds + " seconds.");
                    if (!lock.acquire(waitInSeconds, TimeUnit.SECONDS)) {
                        s_logger.info("Failed to acquire lock: " + lockName);
                        return false;
                    }
                } else { 
                    s_logger.info("Attempting to acquire lock: " + lockName + " for as long as it takes.");
                    lock.acquire(); // will only throw exception or pass
                }

                s_acquiredLocks.put(lockName, lock);
            } else { 
                return false;
            }
            s_logger.info("Acquired lock: " + lockName);
            return true;
        } catch (Exception e) {
            s_logger.error("Acquisition of lock: {} failed with Exception: ", lockName, e);
            return false;
        }
    }

    /**
     * Release the passed lock.
     * 
     * @param lockName The name of the lock to release.
     * 
     * @return true if the lock is released, false otherwise.
     */
    public boolean releaseLock(String lockName) {
        
        if (lockName == null || lockName.isEmpty()) {
            s_logger.info("No lock name specified.");
            return false;
        }
        
        try {
            InterProcessLock lock = s_acquiredLocks.get(lockName);
            if (lock != null) {
                s_acquiredLocks.remove(lockName);
                lock.release();
                s_logger.info("Released lock: " + lockName);             
            } else { 
                return false;
            }
            return true;
        } catch (Exception e) {
            s_logger.error("Release of lock: {} failed with Exception: ", lockName, e);
            return false;
        }
    }
    
    /**
     * Gets a lock name to lock the vplex system on the passed cluster.
     * 
     * @param vplexURI The URI of the VPLEX system.
     * @param clusterId The cluster id.
     * 
     * @return The lock name for the given vplex system and cluster.
     */
    public String getLockName(URI vplexURI, String clusterId) {
        StringBuilder lockNameBuilder = new StringBuilder(vplexURI.toString());
        lockNameBuilder.append(LOCK_NAME_DELIM);
        lockNameBuilder.append(clusterId);
        return lockNameBuilder.toString();
    }
    
    /**
     * Gets a lock name constructed from the vplex system, cluster, and an array.
     * @param vplexURI - URI of vplex system
     * @param clusterId - String cluster id
     * @param arrayURI - Storage array URI
     * @return String lock name that was constructed
     */
    public String getLockName(URI vplexURI, String clusterId, URI arrayURI) {
    	StringBuilder lockNameBuilder = new StringBuilder(getLockName(vplexURI, clusterId));
    	lockNameBuilder.append(LOCK_NAME_DELIM);
    	lockNameBuilder.append(arrayURI.toString());
    	return lockNameBuilder.toString();
    };
    
}
