/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.storagedriver.impl;


import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Implementation of lock manager service for device drivers.
 * Delegates to ControllerLockingService to get distributed lock on CoprHD nodes.
 */
public final class LockManagerImpl implements LockManager {

    private static final Logger log = LoggerFactory.getLogger(LockManagerImpl.class);
    private static LockManagerImpl lockManager;
    private ControllerLockingService lockingService;

    private LockManagerImpl() {
    }

    public static LockManager getInstance(ControllerLockingService lockingService) {
        if (lockManager == null) {
            lockManager = new LockManagerImpl();
            lockManager.setLockingService(lockingService);
        }

        return lockManager;
    }

    @Override
    public boolean acquireLock(String lockName, long timeout, TimeUnit unit) {
        long timeoutSeconds;
        if (timeout == 0 || timeout == -1) {
            timeoutSeconds = timeout;
        } else {
            timeoutSeconds = unit.toSeconds(timeout);
        }
        // delegate to locking service
        log.info("Attempt to acquire lock. Name {}, timeout {} seconds", lockName, timeoutSeconds);
        return lockingService.acquireLock(lockName, timeoutSeconds);
    }

    @Override
    public boolean releaseLock(String lockName) {
        // delegate to locking service
        log.info("Attempt to release lock. Name {}.", lockName);
        return lockingService.releaseLock(lockName);
    }

    private void setLockingService(ControllerLockingService lockingService) {
        this.lockingService = lockingService;
    }
}
