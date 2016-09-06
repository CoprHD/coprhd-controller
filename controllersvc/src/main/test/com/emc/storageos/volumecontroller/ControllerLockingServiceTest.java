/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.emc.storageos.coordinator.client.service.CoordinatorTestBase;
import com.emc.storageos.coordinator.client.service.DistributedPersistentLock;
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
        Assert.assertTrue(impl.acquireLock(LOCKNAME, 10));
        impl.releaseLock(LOCKNAME);
        
        // Lock can be acquired again
        Assert.assertTrue(impl.acquireLock(LOCKNAME, 10));
        Assert.assertTrue(impl.releaseLock(LOCKNAME));
        
        // Lock are re-entrant
        Assert.assertTrue(impl.acquireLock(LOCKNAME, 10));
        Assert.assertTrue(impl.acquireLock(LOCKNAME, 10));
        Assert.assertTrue(impl.releaseLock(LOCKNAME));
        Assert.assertTrue(impl.releaseLock(LOCKNAME));
    }

    @Test
    public void testAcquireReleaseLockMultiThread() throws InterruptedException {
        
        ExecutorService clients = Executors.newFixedThreadPool(NUMCLIENTS);
        for (int i = 0; i < NUMCLIENTS; i++) {
            clients.submit(new Runnable() {
                @Override
                public void run() {
                    String lockName = "TestLock";
                    while (true) {
                        try {
                            log.info(": {} ------ client: starts loop ------");
                            log.info(": {} client trying to acquire lock");
                            String clientName = Thread.currentThread().getName();
                            Thread.sleep(5);
                            boolean bLockActionResult =  impl.acquireLock(lockName, 10);
                            Thread.sleep(5);
                            if (bLockActionResult) {
                                log.info(": request succeeded. doing work {}", clientName);
                                Thread.sleep(10);
                                log.info(": work done. releasing lock");
                                bLockActionResult = impl.releaseLock(lockName);
                                log.info(": lock release status: {};released lock", bLockActionResult);
                                Thread.sleep(10);
                            } else {
                                log.info(": {} request failed. retrying.");
                                Thread.sleep(5);
                            }
                        } catch (InterruptedException e) {
                            // Ignore this.
                        } catch (Exception e) {
                            log.info(": {} transient error ...", e);
                        }
                    }
                }
            });
        }
        clients.awaitTermination(30, TimeUnit.SECONDS);
        log.info("*** testAcquireReleaseLockMultiThread end");
    }
    
    @Test
    public void testAcquireReleasePersistentLock() {
        Assert.assertTrue(impl.acquirePersistentLock(LOCKNAME, CLIENT_A, 10));
        Assert.assertFalse(impl.releasePersistentLock(LOCKNAME, CLIENT_B));
        Assert.assertTrue(impl.releasePersistentLock(LOCKNAME, CLIENT_A));
        
        // Lock again
        Assert.assertTrue(impl.acquirePersistentLock(LOCKNAME, CLIENT_A, 10));
        Assert.assertFalse(impl.acquirePersistentLock(LOCKNAME, CLIENT_B, 10));
        Assert.assertTrue(impl.releasePersistentLock(LOCKNAME, CLIENT_A));
    }
}
