/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedPersistentLock;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.volumecontroller.ControllerLockingService;

public class ControllerLockingServiceImpl implements ControllerLockingService {
    private static final Logger log = LoggerFactory.getLogger(ControllerLockingServiceImpl.class);

    private CoordinatorClient _coordinator;

    private ThreadLocal<Map<String, InterProcessLock>> locks = new ThreadLocal<Map<String, InterProcessLock>>(); 

    /**
     * Sets coordinator
     * 
     * @param coordinator
     */
    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }
    
    @Override
    public boolean acquireLock(String lockName, long seconds) {
        if (lockName == null || lockName.isEmpty()) {
            return false;
        }
        try {
            InterProcessLock lock = getInterProcessLock(lockName);
            log.info("Attempting to acquire lock: " + lockName + (seconds > 0 ? (" for a maximum of " + seconds + " seconds.") : ""));
            return lock.acquire(seconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error(String.format("Acquire of mutex lock: %s failed with Exception: ", lockName), e);
            return false;
        }
    }

    @Override
    public boolean releaseLock(String lockName) {
        if (lockName == null || lockName.isEmpty()) {
            return false;
        }
        try {
            InterProcessLock lock = getInterProcessLock(lockName);
            lock.release();
            synchronized(this) {
                if (!lock.isAcquiredInThisProcess()) {
                    Map<String, InterProcessLock> lockMap = locks.get();
                    lockMap.remove(lockName);     
                }
            }
            log.info("Released lock: " + lockName);
            return true;
        } catch (Exception e) {
            log.error(String.format("Release of mutex lock: %s failed with Exception: ", lockName), e);
        }
        return false;
    }
    
    /**
     * Get InterProcessLock object from thread local. We keep single thread
     * 
     * @param lockName
     * @return InterProcessLock instance
     */
    private InterProcessLock getInterProcessLock(String lockName) {
        Map<String, InterProcessLock> lockMap = locks.get();
        if (lockMap == null) {
            lockMap = new WeakHashMap<String, InterProcessLock>();
            locks.set(lockMap);
        }
        if (lockMap.containsKey(lockName)) {
            return lockMap.get(lockName);
        }
        InterProcessLock lock = _coordinator.getLock(lockName);
        lockMap.put(lockName, lock);
        return lock;
    }
    
    @Override
    public boolean acquirePersistentLock(String lockName, String clientName, long seconds) {
        if (lockName == null || lockName.isEmpty()) {
            return false;
        }
        try {
            Throwable t = null;
            DistributedPersistentLock lock = null;
            boolean acquired = false;
            if (seconds >= 0) {
                log.info("Attempting to acquire lock: " + lockName + (seconds > 0 ? (" for a maximum of " + seconds + " seconds.") : ""));
                while (seconds-- >= 0 && !acquired) {
                    try {
                        lock = _coordinator.getPersistentLock(lockName);
                        acquired = lock.acquireLock(clientName);
                    } catch (CoordinatorException ce) {
                        t = ce;
                        Thread.sleep(1000);
                    }
                }
            } else if (seconds == -1) {
                log.info("Attempting to acquire lock: " + lockName + " for as long as it takes.");
                while (true) {
                    try {
                        lock = _coordinator.getPersistentLock(lockName);
                        acquired = lock.acquireLock(clientName);
                    } catch (CoordinatorException ce) {
                        t = ce;
                        Thread.sleep(1000);
                    }
                }
            } else {
                log.error("Invalid value for seconds to acquireLock");
                return false;
            }

            if (lock == null || !acquired) {
                if (t != null) {
                    log.error(String.format("Acquisition of mutex lock: %s failed with Exception: ", lockName), t);
                } else {
                    log.error(String.format("Acquisition of mutex lock: %s failed", lockName));
                }

                return false;
            }

            log.info("Acquired lock: " + lockName);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public boolean releasePersistentLock(String lockName, String clientName) {
        if (lockName == null || lockName.isEmpty()) {
            return false;
        }
        try {
            DistributedPersistentLock lock = _coordinator.getPersistentLock(lockName);
            if (lock != null) {
                boolean result = lock.releaseLock(clientName);
                log.info("Released lock: " + lockName);
                return result;
            } else {
                log.error(String.format("Release of mutex lock: %s failed: ", lockName));
            }
        } catch (Exception e) {
            log.error(String.format("Release of mutex lock: %s failed with Exception: ", lockName), e);
        }
        return false;
    }
}
