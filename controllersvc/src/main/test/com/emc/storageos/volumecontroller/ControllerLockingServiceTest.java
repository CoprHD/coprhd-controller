/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.emc.storageos.coordinator.client.service.CoordinatorTestBase;
import com.emc.storageos.volumecontroller.impl.ControllerLockingServiceImpl;

public class ControllerLockingServiceTest extends CoordinatorTestBase{
    private static final Logger log = LoggerFactory.getLogger(ControllerLockingServiceTest.class);
    
    private static String LOCKNAME = "TESTLOCK";
    private static String CLIENT_A = "CLIENTA";
    private static String CLIENT_B = "CLIENTB";
    private static int NUMCLIENTS = 5;
    
    private ControllerLockingServiceImpl impl;
    
    public ControllerLockingServiceTest() {}
    
    @Before
    public void setUp() throws Exception {
        impl = new ControllerLockingServiceImpl();
        impl.setCoordinator(this.connectClient());
    }
    
    @Test
    public  void testAcquireReleaseLock() {
        String lockName = getLockName();
        Assert.assertTrue(impl.acquireLock(lockName, 10));
        impl.releaseLock(lockName);
        
        // Lock can be acquired again
        Assert.assertTrue(impl.acquireLock(lockName, 10));
        Assert.assertTrue(impl.releaseLock(lockName));
        
        // Lock are re-entrant
        Assert.assertTrue(impl.acquireLock(lockName, 10));
        Assert.assertTrue(impl.acquireLock(lockName, 10));
        Assert.assertTrue(impl.releaseLock(lockName));
        Assert.assertTrue(impl.releaseLock(lockName));
    }

    @Test
    public void testAcquireReleaseLockTimeout() throws InterruptedException {
        final String lockName = getLockName();
        ExecutorService clients = Executors.newFixedThreadPool(2);
        // Thread 1 - hold the lock
        clients.submit(new Runnable() {
            @Override
            public void run() {
                    try {
                        boolean bLockActionResult =  impl.acquireLock(lockName, 10);
                        Assert.assertTrue(bLockActionResult);
                        log.info("Thread 1 - acquire lock {}", bLockActionResult);
                        Thread.sleep(30000);
                    } catch (InterruptedException e) {
                        // Ignore this.
                    } catch (Exception e) {
                        log.info(": {} transient error ...", e);
                    }
                }
        });

        // Thread 2 - acquire the lock - should fail
        clients.submit(new Runnable() {
            @Override
            public void run() {
                    try {
                        Thread.sleep(10000); // yield so that thread 1 acquired the lock
                        boolean bLockActionResult =  impl.acquireLock(lockName, 10);
                        log.info("Thread 2 - acquire lock {}", bLockActionResult);
                        Assert.assertFalse(bLockActionResult);
                        Thread.sleep(30000);
                    } catch (InterruptedException e) {
                        // Ignore this.
                    } catch (Exception e) {
                        log.info(": {} transient error ...", e);
                    }
                }
        });

        clients.awaitTermination(30, TimeUnit.SECONDS);
        log.info("*** testAcquireReleaseLockTimeout end");
    }
    
    @Test
    public void testAcquireReleaseLockMultiThread() throws InterruptedException {
        final String lockName = getLockName();
        ExecutorService clients = Executors.newFixedThreadPool(NUMCLIENTS);
        for (int i = 0; i < NUMCLIENTS; i++) {
            clients.submit(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            log.info(": {} ------ client: starts loop ------");
                            log.info(": {} client trying to acquire lock");
                            String clientName = Thread.currentThread().getName();
                            boolean bLockActionResult =  impl.acquireLock(lockName, 10);
                            Assert.assertTrue(bLockActionResult);
                            log.info(": request succeeded. doing work {}", clientName);
                            Thread.sleep(2000);
                            log.info(": work done. releasing lock");
                            bLockActionResult = impl.releaseLock(lockName);
                            Assert.assertTrue(bLockActionResult);
                            log.info(": lock release status: {};released lock", bLockActionResult);
                        } catch (InterruptedException e) {
                            // Ignore this.
                        } catch (Exception e) {
                            log.info(": {} transient error ...", e);
                        }
                    }
                }
            });
        }
        clients.awaitTermination(60, TimeUnit.SECONDS);
        log.info("*** testAcquireReleaseLockMultiThread end");
    }
    
    @Test
    public void testAcquireReleasePersistentLock() {
        final String lockName = getLockName();
        Assert.assertTrue(impl.acquirePersistentLock(lockName, CLIENT_A, 10));
        Assert.assertFalse(impl.releasePersistentLock(lockName, CLIENT_B));
        Assert.assertTrue(impl.releasePersistentLock(lockName, CLIENT_A));
        
        // Lock again
        Assert.assertTrue(impl.acquirePersistentLock(lockName, CLIENT_A, 10));
        Assert.assertFalse(impl.acquirePersistentLock(lockName, CLIENT_B, 10));
        Assert.assertTrue(impl.releasePersistentLock(lockName, CLIENT_A));
    }
    
    private String getLockName() {
        return LOCKNAME + new Random(System.currentTimeMillis()).nextInt();
    }
}
