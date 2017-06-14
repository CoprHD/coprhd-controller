/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.locking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedAroundHook;
import com.emc.storageos.coordinator.client.service.DistributedDataManager;
import com.emc.storageos.coordinator.client.service.DistributedLockQueueManager;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.exceptions.DeviceControllerException;

public class DistributedOwnerLockServiceImpl implements DistributedOwnerLockService {
    /**
     * 
     */
    private static final int SLEEP_MS_BETWEEN_ACQUIRE_ATTEMPTS = 10000;
    private static final Logger log = LoggerFactory.getLogger(DistributedOwnerLockServiceImpl.class);
    private CoordinatorClient coordinator;
    private DistributedDataManager dataManager;
    private DistributedLockQueueManager lockQueueManager;

    @Override
    public boolean acquireLocks(List<String> lockKeys, String owner, long seconds) {
        // Sort the lockKeys to maintain the same lock order.
        Collections.sort(lockKeys);
        for (int i = 0; i < lockKeys.size(); i++) {
            boolean wasLocked = acquireLock(lockKeys.get(i), owner, seconds);
            if (wasLocked == false) {
                log.error("Error - Releasing all previously acquired locks");
                for (int j = 0; j < i; j++) {
                    releaseLock(lockKeys.get(j), owner);
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean acquireLocks(List<String> lockKeys, String owner,
                                long lockingStartedTimeSeconds, long maxLockWaitSeconds)
            throws LockRetryException {
        Long currentTimeSeconds = System.currentTimeMillis() / 1000;
        Long remainingTimeSeconds = lockingStartedTimeSeconds + maxLockWaitSeconds - currentTimeSeconds;
        if (remainingTimeSeconds < 0) {
            log.info("Unable to acquire lock within the maximum waiting time {} seconds", maxLockWaitSeconds);
            return false;      // We've waited the maximum amount of time
        }
        LockRetryException lockRetryThrowable = null;
        // Sort the lockKeys to maintain the same lock order.
        Collections.sort(lockKeys);
        for (int i=0; i < lockKeys.size(); i++) {
            // Poll, since we are going to throw an exception if cannot get lock.
            boolean wasLocked = acquireLock(lockKeys.get(i), owner, lockingStartedTimeSeconds, 0);
            if (wasLocked == false) {
                String lockPath = getLockDataPath(lockKeys.get(i));
                lockRetryThrowable = new LockRetryException(lockPath, remainingTimeSeconds);
                log.error("Error - Releasing all previously acquired locks");
                for (int j=0; j < i; j++) {
                    releaseLock(lockKeys.get(j), owner);
                }
                throw lockRetryThrowable;
            }
        }
        return true;
    }

    @Override
    public boolean releaseLocks(List<String> lockKeys, String owner) {
        // Sort the lockKeys to maintain the same lock order.
        Collections.sort(lockKeys);
        boolean returnVal = true;
        for (int i = 0; i < lockKeys.size(); i++) {
            boolean returned = releaseLock(lockKeys.get(i), owner);
            if (returned == false) {
                returnVal = false;
            }
        }
        return returnVal;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.locking.DistributedOwnerLockService#releaseLocks(java.lang.String)
     */
    @Override
    public boolean releaseLocks(String owner) {
        List<String> lockKeys = getLocksForOwner(owner);
        boolean released = false;
        if (lockKeys == null || lockKeys.isEmpty()) {
            // no locks to release
            log.debug(String.format("lock owner: %s has no locks to unlock", owner));
            released = true;
        } else {
            log.info(String.format("releasing locks %s", StringUtils.join(lockKeys.toArray())));
            released = releaseLocks(lockKeys, owner);
        }
        return released;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.locking.DistributedOwnerLockService#getLocksForOwner(java.lang.String)
     */
    @Override
    public List<String> getLocksForOwner(String owner) {

        String ownerPath = getOwnerPath(owner);
        try {
            Stat stat = dataManager.checkExists(ownerPath);
            if (stat != null) {
                List<String> locks = dataManager.getChildren(ownerPath);
                if (locks != null) {
                    return locks;
                }
            }
        } catch (Exception ex) {
            log.error("Can't get locks for owner: " + owner, ex);
        }
        return new ArrayList<String>();
    }

    @Override
    public boolean acquireLock(String lockKey, String owner, long maxWaitSeconds) {
        return acquireLock(lockKey, owner, (System.currentTimeMillis() / 1000), maxWaitSeconds);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.impl.DistributedOwnerLock#acquireLock(java.lang.String, java.lang.String, long)
     */
    @Override
    public boolean acquireLock(String lockKey, String owner, long lockingStartedTimeSeconds, long maxWaitSeconds) {
        boolean acquired = false;
        long waitTime = 0;
        InterProcessLock lock = null;
        boolean reportedLongLock = false;
        boolean reportedBlocking = false;
        do {
            long currentTime = System.currentTimeMillis();
            try {
                // Get semaphore
                lock = lockIPL(lockKey);
                if (lock != null) {
                    // Get the lock data.
                    DistributedOwnerLockData data = loadLockData(lockKey);
                    // If no data, then we got the lock
                    if (data == null) {
                        data = new DistributedOwnerLockData(owner, currentTime);
                        persistLockData(lockKey, data);
                        acquired = true;
                    } else {
                        // If we're already the owner, that's fine.
                        if (data.owner.equals(owner)) {
                            acquired = true;
                        } else if (!reportedLongLock && currentTime / 1000 > data.timeAcquired + 3600) {
                            reportedLongLock = true;
                            log.info("Lock held more than 1 hour: " + lockKey + " owner: " + data.owner);
                        }
                    }
                }
            } finally {
                unlockIPL(lock);
            }
            // Report the time to acquire the lock if acquired.
            if (acquired) {
                log.info(String.format("Lock %s owner %s acquired after %d seconds", lockKey, owner,
                        (currentTime / 1000) - lockingStartedTimeSeconds));
            }
            // Sleep if we did not acquire the lock and want to block
            else if (maxWaitSeconds > 0) {
                try {
                    if (!reportedBlocking) {
                        reportedBlocking = true;
                        log.info(String.format("Owner %s blocking to wait for lock %s maxWaitSeconds %d", owner, lockKey, maxWaitSeconds));
                    }
                    Thread.sleep(SLEEP_MS_BETWEEN_ACQUIRE_ATTEMPTS);
                } catch (Exception ex) {
                    log.error(ex.getMessage(), ex);
                }
            }
            waitTime = (System.currentTimeMillis() / 1000) - lockingStartedTimeSeconds;
        } while (!acquired && waitTime < maxWaitSeconds);
        if (!acquired && maxWaitSeconds > 0 && waitTime >= maxWaitSeconds) {
            log.info("Timeout waiting on lock: " + lockKey + " owner: " + owner);
        }
        return acquired;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.impl.DistributedOwnerLock#releaseLock(java.lang.String, java.lang.String)
     */
    @Override
    public boolean releaseLock(String lockName, String owner) {
        log.info(String.format("releasing lockName: %s owner: %s", lockName, owner));
        InterProcessLock lock = null;
        boolean retval = false;
        try {
            // Get semaphore
            lock = lockIPL(lockName);
            DistributedOwnerLockData data = loadLockData(lockName);
            if (data != null) {
                if (!data.owner.equals(owner)) {
                    log.error(String.format("Failed to release lock: %s for owner: %s because lock held by another owner: %s",
                            lockName, owner, data.getOwner()));
                    throw DeviceControllerException.exceptions.failedToReleaseLock(lockName);
                }
                // remove the lock data
                removeLockData(lockName, data.getOwner());
                Long heldTime = (System.currentTimeMillis() - data.timeAcquired) / 1000;
                log.info(String.format("Lock %s released after %d seconds", lockName, heldTime));
            } else {
                log.info(String.format("unable to unlock lockname: %s owner: %s lock not found in zk", lockName, owner));
            }
            retval = true;

            // Trigger a dequeue event for any items on the DistributedLockQueue waiting for this particular lock
            checkLockQueueAndDequeue(lockName);
        } finally {
            unlockIPL(lock);
        }
        return retval;
    }

    @Override
    public boolean isDistributedOwnerLockAvailable(String lockName) throws Exception {
        return coordinator.isDistributedOwnerLockAvailable(getLockDataPath(lockName));
    }

    /**
     * Returns a concrete implementation of the {@link DistributedAroundHook} class.
     *
     * This allows users of this instance to wrap arbitrary code with before and after hooks that lock and unlock
     * the "globalLock" IPL, respectively.
     *
     * @return A DistributedAroundHook instance.
     */
    @Override
    public DistributedAroundHook getDistributedOwnerLockAroundHook() {
        return new DistributedAroundHook() {

            private InterProcessLock lock;

            @Override
            public boolean before() {
                lock = lockIPL(null);
                return lock != null;
            }

            @Override
            public void after() {
                unlockIPL(lock);
            }
        };
    }

    /**
     * Returns the path name for the distOwnerLock
     * 
     * @param lockKey
     * @return
     */
    private String getLockPath(String lockKey) {
        return "distOwnerLock/globalLock";
    }

    /**
     * Return the path for the lock data.
     * 
     * @param lockKey
     * @return
     */
    private String getLockDataPath(String lockKey) {
        return ZkPath.LOCKDATA.toString() + "/distOwnerLock/locks/" + lockKey;
    }

    /**
     * Return the path for look up lock by owner
     * 
     * @param lockKey
     * @param owner
     * @return
     */
    private String getLockByOwnerPath(String lockKey, String owner) {
        return ZkPath.LOCKDATA.toString() + "/distOwnerLock/" + owner + "/" + lockKey;
    }

    /**
     * Return the path for the owner
     * 
     * @param owner
     * @return
     */
    private String getOwnerPath(String owner) {
        return ZkPath.LOCKDATA.toString() + "/distOwnerLock/" + owner;
    }

    /**
     * Get the InterProcessLock.
     * 
     * @param lockKey -- the name of the Lock
     * @return InterProcessLock
     */
    private InterProcessLock getIPLock(String lockKey) {
        try {
            InterProcessLock lock = coordinator.getLock(getLockPath(lockKey));
            return lock;
        } catch (Exception ex) {
            log.error("Could not get InterProcessLock: " + lockKey, ex);
        }
        return null;
    }

    /**
     * Locks an InterProcessLock using ZK
     * 
     * @SlockName
     * @return true if lock acquired, null if not
     */
    private InterProcessLock lockIPL(String lockKey) {
        boolean acquired = false;
        InterProcessLock lock = getIPLock(lockKey);
        if (lock == null) {
            return null;
        }
        try {
            acquired = lock.acquire(60, TimeUnit.MINUTES);
            if (acquired) {
                return lock;
            }
        } catch (Exception ex) {
            log.error("Exception locking IPL: " + lockKey, ex);
        }
        log.error("Unable to acquire IPL: " + lockKey);
        return null;
    }

    /**
     * Unlocks an InterProcessLock using ZK
     * 
     * @param lock InterProcessLock
     */
    private void unlockIPL(InterProcessLock lock) {
        try {
            if (lock != null) {
                lock.release();
            }
        } catch (Exception ex) {
            log.error("Exception unlocking IPL: " + lock.toString(), ex);
        }
    }

    /**
     * Retrieve lock data for a class.
     * 
     * @param lockName - The lock name.
     * @return -- A Java serializable object or null;
     */
    private DistributedOwnerLockData loadLockData(String lockName) {
        String path = getLockDataPath(lockName);
        try {
            if (dataManager.checkExists(path) == null) {
                return null;
            }
            DistributedOwnerLockData data = (DistributedOwnerLockData) dataManager.getData(path, false);
            return data;
        } catch (Exception ex) {
            log.error("Exception loading LockData: " + path, ex);
            return null;
        }
    }

    /**
     * Update the LockData in ZK.
     * 
     * @param lockName
     * @param data - LockData
     */
    private void persistLockData(String lockName, DistributedOwnerLockData data) {
        String path = getLockDataPath(lockName);
        String ownerLockPath = getLockByOwnerPath(lockName, data.getOwner());
        try {
            // store a reference from the owner id to the lock id
            Stat stat = dataManager.checkExists(ownerLockPath);
            if (stat == null) {
                dataManager.createNode(ownerLockPath, false);
            }
            // store the lock data
            stat = dataManager.checkExists(path);
            if (stat == null) {
                dataManager.createNode(path, false);
            }
            dataManager.putData(path, data);
        } catch (Exception ex) {
            log.error("Can't storage LockData: " + lockName, ex);
        }
    }

    /**
     * Remove LockData
     * 
     * @param lockName
     */
    private void removeLockData(String lockName, String owner) {
        try {
            // remove the lock data
            String path = getLockDataPath(lockName);
            Stat stat = dataManager.checkExists(path);
            if (stat != null) {
                dataManager.removeNode(path);
            }
            // remove the owners reference to the lock
            String ownerLockPath = getLockByOwnerPath(lockName, owner);
            stat = dataManager.checkExists(ownerLockPath);
            if (stat != null) {
                dataManager.removeNode(ownerLockPath);
            }
            // if the owner has no remaining locks, remove the owner node
            String ownerPath = getOwnerPath(owner);
            List<String> remainingLocks = dataManager.getChildren(ownerPath);
            if (remainingLocks == null || remainingLocks.isEmpty()) {
                dataManager.removeNode(ownerPath);
            }
        } catch (Exception ex) {
            log.error("Can't remove LockData: " + lockName, ex);
        }
    }

    private void checkLockQueueAndDequeue(String lockKey) {
        if (lockKey == null) {
            return;
        }
        boolean wasDequeued = lockQueueManager.dequeue(lockKey);
        if (wasDequeued) {
            log.info("A task from lock group {} was dequeued.", lockKey);
        }
    }

    /**
     * Start the service.
     */
    public void start() {
        log.info("DistributedOwnerLockService starting up");
        try {
            dataManager = coordinator.getWorkflowDataManager();
        } catch (Exception ex) {
            log.error("Can't get a DistributedDataManager", ex);
        }
    }

    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public DistributedDataManager getDataManager() {
        return dataManager;
    }

    public void setDataManager(DistributedDataManager dataManager) {
        this.dataManager = dataManager;
    }

    public void setLockQueueManager(DistributedLockQueueManager lockQueueManager) {
        this.lockQueueManager = lockQueueManager;
    }
}
