package com.emc.storageos.storagedriver.impl;


import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.volumecontroller.ControllerLockingService;

import java.util.concurrent.TimeUnit;

/**
 * Implementation of lock manager service for device drivers.
 */
public final class LockManagerImpl implements LockManager {

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
        return false;
    }

    @Override
    public void releaseLock(String lockName) {

    }

    private void setLockingService(ControllerLockingService lockingService) {
        this.lockingService = lockingService;
    }
}
