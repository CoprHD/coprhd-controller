/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.storagedriver.impl;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.storagedriver.LockManager;

/**
 * Implementation of lock manager service for device drivers.
 * Delegates to the Coordinator to get a Apache Curator 
 * InterProcessLock. Note that InterProcessLock requires the
 * acquiring thread to release the lock.
 */
public final class LockManagerImpl implements LockManager {

    // The singleton instance.
    private static LockManagerImpl lockManager;

    // A reference to the coordinator.
    private CoordinatorClient coordinator;

    // A map of the acquired locks.
    private static ConcurrentHashMap<String, InterProcessLock> acquiredLocks = new ConcurrentHashMap<String, InterProcessLock>();

    // A reference to the logger.
    private static final Logger log = LoggerFactory.getLogger(LockManagerImpl.class);


    /**
     * Private default constructor
     */
    private LockManagerImpl() {
    }

    /**
     * Gets the singleton instance, creating it if necessary.
     * 
     * @param coordinator A reference to the coordinator client.
     * 
     * @return A reference to the lock manager instance.
     */
    public static LockManager getInstance(CoordinatorClient coordinator) {
        if (lockManager == null) {
            lockManager = new LockManagerImpl();
            lockManager.setCoordinator(coordinator);
        }
        return lockManager;
    }

    @Override
    public boolean acquireLock(String lockName, long timeout, TimeUnit unit) {
        
        // Verify the lock name.
        if (lockName == null || lockName.isEmpty()) {
            log.info("No lock name specified.");
            return false;
        }
        
        // Covert wait time to seconds.
        long timeoutSeconds;
        if (timeout == 0 || timeout == -1) {
            timeoutSeconds = timeout;
        } else {
            timeoutSeconds = unit.toSeconds(timeout);
        }

        try {
            InterProcessLock lock = coordinator.getLock(lockName);
            if (lock != null) {
                if (timeoutSeconds >= 0) {
                    log.info("Attempting to acquire lock: " + lockName + " for a maximum of " + timeoutSeconds + " seconds.");
                    if (!lock.acquire(timeoutSeconds, TimeUnit.SECONDS)) {
                        log.info("Failed to acquire lock: " + lockName);
                        return false;
                    }
                } else {
                    log.info("Attempting to acquire lock: " + lockName + " for as long as it takes.");
                    lock.acquire(); // will only throw exception or pass
                }

                acquiredLocks.put(lockName, lock);
            } else {
                return false;
            }
            log.info("Acquired lock: " + lockName);
            return true;
        } catch (Exception e) {
            log.error("Acquisition of lock: {} failed with Exception: ", lockName, e);
            return false;
        }
    }

    @Override
    public boolean releaseLock(String lockName) {
        if (lockName == null || lockName.isEmpty()) {
            log.info("No lock name specified.");
            return false;
        }

        try {
            InterProcessLock lock = acquiredLocks.get(lockName);
            if (lock != null) {
                lock.release();
                acquiredLocks.remove(lockName);
                log.info("Released lock: " + lockName);
            } else {
                log.info("Unknown lock: " + lockName);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Release of lock: {} failed with Exception: ", lockName, e);
            return false;
        }
    }

    /**
     * Setter for the coordinator client.
     * 
     * @param coordinator A reference to the coordinator client.
     */
    private void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }
}
