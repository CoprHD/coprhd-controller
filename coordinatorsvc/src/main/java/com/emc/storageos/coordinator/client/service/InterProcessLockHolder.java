/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.coordinator.client.service;

import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * This class holds a coordinator lock. Because this class implements AutoCloseable,
 * it enables caller to use Java 7 syntax like:
 * try (LockHolder lock = new LockHolder(...)) { ... }
 */
public class InterProcessLockHolder implements AutoCloseable {

    InterProcessLock lock;
    Logger log;
    String name;

    private InterProcessLockHolder() {
    }

    public static InterProcessLockHolder acquire(CoordinatorClient client, String lockName, Logger log, int timeoutMillis) throws Exception {
        InterProcessLock lock = client.getLock(lockName);
        if (!lock.acquire(timeoutMillis, TimeUnit.MILLISECONDS)) {
            if (log != null) {
                log.info("Failed to take lock {} in {} ms", lockName, timeoutMillis);
            }
            return null;
        }

        if (log != null) {
            log.info("Acquired lock: {}", lockName);
        }

        InterProcessLockHolder holder = new InterProcessLockHolder();
        holder.lock = lock;
        holder.name = lockName;
        holder.log = log;

        return holder;
    }

    public InterProcessLockHolder(CoordinatorClient client, String lockName, Logger log) throws Exception {
        this.name = lockName;
        this.log = log;

        this.lock = client.getLock(lockName);
        this.lock.acquire();

        if (this.log != null) {
            this.log.info("Acquired lock: {}", this.name);
        }
    }

    public InterProcessLock getLock() {
        return this.lock;
    }

    @Override
    public void close() throws Exception {
        if (this.lock != null) {
            try {
                this.lock.release();
            } catch (Exception e) {
                if (this.log != null) {
                    this.log.error(String.format("Failed to release lock: %s", this.name), e);
                }
                throw e;
            }
            if (this.log != null) {
                this.log.info("Released lock {}", this.name);
            }
            this.lock = null;
        }
    }
}