/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.zookeeper;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

/**
 * Singleton service that runs on only one node at a given time.
 * 
 * @author jonnymiller
 */
public abstract class SingletonService implements Runnable {
    protected static final long DEFAULT_ERROR_RETRY_DELAY = 15000;
    protected final Logger log = Logger.getLogger(getClass());
    @Autowired
    private CoordinatorClient coordinatorClient;
    private long errorRetryDelay = DEFAULT_ERROR_RETRY_DELAY;
    private volatile Thread thread;
    private InterProcessLock lock;

    public CoordinatorClient getCoordinatorClient() {
        return coordinatorClient;
    }

    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    public long getErrorRetryDelay() {
        return errorRetryDelay;
    }

    public void setErrorRetryDelay(long errorRetryDelay) {
        this.errorRetryDelay = errorRetryDelay;
    }

    @PostConstruct
    public final void start() {
        log.debug("Starting " + getClass().getSimpleName());
        thread = new Thread(this);
        thread.start();
    }

    @PreDestroy
    public final void stop() {
        log.debug("Stopping " + getClass().getSimpleName());
        Thread runningThread = thread;
        thread = null;

        if (runningThread != null) {
            stopService();
            runningThread.interrupt();
            try {
                runningThread.join();
            }
            catch (InterruptedException e) {
                log.error("Interrupted waiting for successful termination", e);
            }
        }
    }

    @Override
    public void run() {
        String serviceName = getClass().getSimpleName();
        boolean error = false;
        try {
            Thread runningThread = Thread.currentThread();
            thread = runningThread;
            while ((thread == runningThread) && !runningThread.isInterrupted()) {
                if (error) {
                    log.info(String.format("Pausing %s for %dms due to error", serviceName, errorRetryDelay));
                    Thread.sleep(errorRetryDelay);
                    error = false;
                }

                if (acquireLock()) {
                    try {
                        log.info("Acquired singleton service " + serviceName);
                        runService();
                    }
                    catch (RuntimeException e) {
                        log.error("Singleton service " + serviceName + " failed", e);
                        error = true;
                    }
                    finally {
                        releaseLock();
                        log.info("Released singleton service " + serviceName);
                    }
                }
            }
        }
        catch (InterruptedException e) {
            log.warn("Singleton service " + serviceName + " interrupted", e);
        }
    }

    private boolean acquireLock() {
        log.debug("Acquiring lock");
        lock = coordinatorClient.getLock(getClass().getName());
        return acquireLock(lock);
    }

    private void releaseLock() {
        releaseLock(lock);
        lock = null;
    }

    protected boolean acquireLock(InterProcessLock lock) {
        try {
            lock.acquire();
            log.debug("Acquired lock");
            return true;
        }
        catch (Exception e) {
            log.error("Error acquiring lock", e);
            return false;
        }
    }

    protected void releaseLock(InterProcessLock lock) {
        try {
            if (lock != null) {
                lock.release();
                log.debug("Released lock");
            }
        }
        catch (Exception e) {
            log.error("Error releasing lock", e);
        }
    }

    /**
     * Runs the singleton service after the lock is acquired.
     */
    protected abstract void runService();

    /**
     * Stops a running singleton service.
     */
    protected abstract void stopService();
}
