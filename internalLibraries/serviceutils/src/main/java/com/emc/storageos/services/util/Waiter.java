/*
 * Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.services.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A reference class for implementing sleep/wakeup semantics based on Java condition variables.
 * Currently it's being used by Upgrade/Property/SecretsManager of syssvc but may as well be used elsewhere.
 */
public class Waiter {

    // just to differentiate the default and wakeup value
    private long t = -1;
    private Lock lock = new ReentrantLock();
    Condition wakeup = lock.newCondition();
    private static final Logger logger = LoggerFactory.getLogger(Waiter.class);

    /**
     * Sleep for a specific amount of time, or until the wakeup method is called, whichever comes first
     * 
     * @param milliSeconds the amount of time to sleep, in milliseconds.
     */
    public void sleep(long milliSeconds) {
        lock.lock();
        try {
            // if someone tried to wake it up before it goes to sleep
            // just reset t and restart the loop
            if (t != 0) {
                t = System.currentTimeMillis() + milliSeconds;
                while (true) {
                    final long dt = t - System.currentTimeMillis();
                    if (dt <= 0) {
                        break;
                    } else {
                        wakeup.await(dt, TimeUnit.MILLISECONDS);
                    }
                }
            }
            // reset t since it may have been set to 0 while sleeping
            t = System.currentTimeMillis();
        } catch (InterruptedException e) {
        	logger.error(e.getMessage(),e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Wakeup a sleeping thread
     * If the thread is not sleeping, it will skip the sleep immediately followed.
     */
    public void wakeup() {
        lock.lock();
        try {
            t = 0;
            wakeup.signal();
        } finally {
            lock.unlock();
        }
    }
}
