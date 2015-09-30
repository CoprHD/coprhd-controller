/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices;

import org.junit.Test;

import com.emc.storageos.services.util.Waiter;

class Sleeper implements Runnable {
    private final Waiter _waiter;
    private final long _startTimeMillis;

    public Sleeper(final Waiter waiter) {
        _waiter = waiter;
        _startTimeMillis = System.currentTimeMillis();
    }

    public void run() {
        for (int i = 0; i < 5; i++) {
            System.out.println("A: sleep(1000): " + (System.currentTimeMillis() - _startTimeMillis));
            _waiter.sleep(1000);
        }
    }
}

public class WaiterTest {

    @Test
    public void waiterTest() {
        final Waiter waiter = new Waiter();
        Thread t = new Thread(new Sleeper(waiter));
        t.start();
        try {
            Thread.sleep(100);
            t.interrupt();
            Thread.sleep(400);
            System.out.println("B: wakeup");
            waiter.wakeup();
            Thread.sleep(2500);
            System.out.println("B: wakeup");
            waiter.wakeup();
        } catch (Exception e) {
            System.out.println("B: " + e);
        }
    }

}
