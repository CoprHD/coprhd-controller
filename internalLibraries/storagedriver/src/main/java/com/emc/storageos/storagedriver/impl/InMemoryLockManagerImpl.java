/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.emc.storageos.storagedriver.LockManager;

/**
 * In memory implementation of LockManager.
 * This implementation provides re-entrant locks.
 *
 * Can be used for driver verification/testing.
 */
public class InMemoryLockManagerImpl implements LockManager {

    // lock name to holding thread name map
    private Map<String, String> locks = new HashMap<>();

    // lock name to map of thread names waiting for this lock.
    // map of threads: key --- thread name, time to stop waiting
    private Map<String, Map<String, Long>> lockNameToThreads = new HashMap<>();

    @Override
    public synchronized boolean acquireLock(String lockName, long timeout, TimeUnit unit) {

        long timeoutMilliSeconds = TimeUnit.MILLISECONDS.convert(timeout, unit);
        String threadName = Thread.currentThread().getName();
        Map<String, Long> threadToTime = lockNameToThreads.get(lockName);
        if (threadToTime == null) {
            threadToTime = new HashMap<>();
            lockNameToThreads.put(lockName, threadToTime);
        }

        Long waitUntil = System.currentTimeMillis() + timeoutMilliSeconds;
        while (!(locks.get(lockName) == null || locks.get(lockName).equals(threadName))) {
            // locked by other thread
            Long timeRemaining;
            if (threadToTime.get(threadName) != null) {
                // thread is already  waiting. adjust remaining wait time.
                timeRemaining = threadToTime.get(threadName)-System.currentTimeMillis();
            } else {
                // first attempt to lock by the thread
                threadToTime.put(threadName, waitUntil);
                timeRemaining = timeoutMilliSeconds;
            }

            if (timeRemaining <= 0) {
                // timeout elapsed
                threadToTime.remove(threadName);
                return false;
            }
            try {
                wait(timeRemaining);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
        // lock is available
        threadToTime.remove(threadName);
        locks.put(lockName, threadName);
        return true;

    }

    @Override
    public synchronized boolean releaseLock(String lockName) {

        locks.remove(lockName);
        notifyAll();
        return true;
    }
}
