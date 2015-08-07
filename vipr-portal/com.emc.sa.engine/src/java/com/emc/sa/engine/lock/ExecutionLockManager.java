/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine.lock;

import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.google.common.collect.Maps;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

/**
 * This is a helper class that is constructed for each execution, allowing locks to be acquired and released.
 * 
 * @author jonnymiller
 */
public class ExecutionLockManager {
    private static final Logger LOG = Logger.getLogger(ExecutionLockManager.class);
    private CoordinatorClient coordinator;
    private Map<String, LockState> locks = Maps.newLinkedHashMap();

    public ExecutionLockManager(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public Set<String> getLocks() {
        return locks.keySet();
    }

    /**
     * Acquires a named lock.
     * 
     * @param name
     *            the name of the lock.
     * @return true if the lock was acquired.
     */
    public boolean acquireLock(String name) {
        LockState state = getLock(name);
        return acquireLock(name, state);
    }

    /**
     * Releases the named lock.
     * 
     * @param name
     *            the name of the lock.
     */
    public void releaseLock(String name) {
        LockState state = getLock(name);
        releaseLock(name, state);
    }

    /**
     * Destroys any held locks that may be remaining.
     */
    public void destroyLocks() {
        for (Map.Entry<String, LockState> entry : locks.entrySet()) {
            String name = entry.getKey();
            LockState state = entry.getValue();

            // Release as many locks as were acquired.
            int acquiredCount = state.count;
            for (int i = 0; i < acquiredCount; i++) {
                releaseLock(name, state);
            }
        }
    }

    protected LockState getLock(String name) {
        LockState state = locks.get(name);
        if (state == null) {
            state = new LockState();
            state.lock = coordinator.getLock(name);
            locks.put(name, state);
        }
        return state;
    }

    protected boolean acquireLock(String name, LockState state) {
        try {
            state.lock.acquire();
            state.count++;
            LOG.debug("Acquired lock '" + name + "'");
            return true;
        } catch (Exception e) {
            LOG.error("Error acquiring lock '" + name + "'", e);
            return false;
        }
    }

    protected void releaseLock(String name, LockState state) {
        try {
            if (state != null) {
                state.lock.release();

                if (state.count > 0) {
                    state.count--;
                }
                else {
                    LOG.warn("Release called on lock '" + name + "' which does not appear to have been acquired");
                }
            }
        } catch (Exception e) {
            LOG.error("Error releasing lock '" + name + "'", e);
        }
    }

    private static class LockState {
        private InterProcessLock lock;
        private int count;
    }
}
