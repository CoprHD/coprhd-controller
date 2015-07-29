/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.services.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A reference class for implementing sleep/wakeup semantics based on Java condition variables.
 * Currently it's being used by Upgrade/Property/SecretsManager of syssvc but may as well be used elsewhere.
 */
public class Waiter {

    // just to differentiate the default and wakeup value
    private long t = -1;
    private Lock lock = new ReentrantLock();
    Condition wakeup = lock.newCondition();

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
