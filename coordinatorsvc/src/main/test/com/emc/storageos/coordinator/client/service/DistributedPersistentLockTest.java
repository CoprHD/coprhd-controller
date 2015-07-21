/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service;


import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * DistributedPersistentLockTest unit test
 */
public class DistributedPersistentLockTest extends CoordinatorTestBase {
    private static final Logger _logger = LoggerFactory.getLogger(DistributedPersistentLockTest.class);
    private final int NUMRUNS = 5;
    private final int NUMCLIENTS = 10;

    /**
     * Simulates a client acquiring and releasing persistent locks.
     * Also simulates failure to release persistent locks when attempted by incorrect owner.
     * Repeated NUMCLIENTS times.
     * @throws Exception
     */
    @Test
    public void acquireAndReleaseDistributedPersistentLock() throws Exception {
        _logger.info("*** acquireAndReleaseDistributedPersistentLock start");
        List<DistributedPersistentLock> locks = new ArrayList<DistributedPersistentLock>();
        for(int i=0; i<NUMRUNS; i++) {
            String lockName = String.format("%s-%s", "lock", i);
            CoordinatorClient testClient = connectClient();
            try {
                locks.add(testClient.getPersistentLock(lockName));
            } catch (Exception e) {
                _logger.info(": Problem when instantiating lock {}", lockName, e);
                Assert.assertNull(e);
            }
            _logger.info(": Initialized lock: {}", lockName);
            String clientName = "Tester" + i;
            String failClientName = "xyz";
            String ownerName = null;
            boolean bLockActionResult;
            try {
                _logger.info(": {} trying to acquire lock: {}", clientName, lockName);
                bLockActionResult = locks.get(i).acquireLock(clientName);
                _logger.info(": Lock acquire result: {}", bLockActionResult);
                ownerName = locks.get(i).getLockOwner();
                _logger.info(": Lock held by: {}", ownerName);
                Assert.assertTrue(ownerName.equals(clientName));
                _logger.info(": Verified lock: {} is held by: {}", lockName, clientName);
            } catch (Exception e) {
                Assert.assertNull(e);
            }
            try {
                ownerName = locks.get(i).getLockOwner();
                _logger.info("Curr owner: {}", ownerName);
                _logger.info("Check if {} is owner: {}", failClientName, failClientName.equals(ownerName));
                _logger.info(": {} trying to release lock: {}", failClientName, lockName);
                bLockActionResult = locks.get(i).releaseLock(failClientName);
                _logger.info(": Lock release result: {}", bLockActionResult);
            } catch (Exception e) {
                Assert.assertNotNull(e);
                _logger.info(": {} Failed to release lock: {}", failClientName, lockName);
            }
            ownerName = locks.get(i).getLockOwner();
            Assert.assertNotNull(ownerName);
            _logger.info(": {} is owner of lock: {}", ownerName, lockName);
            try {
                _logger.info(": Releasing lock: {}", lockName);
                locks.get(i).releaseLock(clientName);
                _logger.info(": Released lock: {}", lockName);
            } catch (Exception e) {
                Assert.assertNull(e);
            }
            _logger.info(": Verify lock: {} is free", lockName);
            ownerName = locks.get(i).getLockOwner();
            _logger.info(": {} is owner of lock: {}", ownerName, lockName);
            Assert.assertNull(ownerName);
            _logger.info(": Reacquire lock: {}", lockName);
            try {
                _logger.info(": {} trying to acquire lock: {}", clientName, lockName);
                bLockActionResult = locks.get(i).acquireLock(clientName);
                _logger.info(": Lock acquire result: {}", bLockActionResult);
                ownerName = locks.get(i).getLockOwner();
                _logger.info(": Lock held by: {}", ownerName);
                Assert.assertTrue(ownerName.equals(clientName));
                _logger.info(": Verified lock: {} is held by: {}", lockName, clientName);
            } catch (Exception e) {
                Assert.assertNull(e);
            }
        }
        _logger.info("*** acquireAndReleaseDistributedPersistentLock end");
    }

    /**
     * Simulates a client acquiring persistent locks.
     * Also simulates failure to acquire persistent locks when attempted on granted locks.
     * Repeated NUMCLIENTS times.
     * @throws Exception
     */
    @Test
    public void acquireDistributedPersistentLock() throws Exception {
        _logger.info("*** acquireDistributedPersistentLock start");
        List<DistributedPersistentLock> locks = new ArrayList<DistributedPersistentLock>();
        for(int i=0; i<NUMRUNS; i++) {
            String lockName = String.format("%s-%s", "lock", i);
            DistributedPersistentLock lock = null;
            try {
                locks.add(connectClient().getPersistentLock(lockName));
            } catch (Exception e) {
                _logger.info(": Problem when instantiating lock: {}", lockName, e);
                Assert.assertNull(e);
            }
            _logger.info(": Initialized lock: {}", lockName);
            try {
                String clientName = "Tester" + i;
                _logger.info(": {} trying to acquire lock: {}", clientName, lockName);
                boolean bLockActionResult = locks.get(i).acquireLock(clientName);
                _logger.info(": Lock acquire result: {}", bLockActionResult);
                String ownerName = locks.get(i).getLockOwner();
                _logger.info(": Lock held by: {}", ownerName);
                Assert.assertTrue(ownerName.equals(clientName));
                _logger.info(": Verified lock: {} is held by: {}", lockName, ownerName);
                String failClientName = "xyz";
                _logger.info(": {} trying to acquire lock: {}", failClientName, lockName);
                bLockActionResult = locks.get(i).acquireLock(failClientName);
                _logger.info(": Lock acquire result: {}", bLockActionResult);
                ownerName = locks.get(i).getLockOwner();
                _logger.info(": Lock held by: {}", ownerName);
                Assert.assertFalse(ownerName.equals(failClientName));
                _logger.info(": Lock held by: {}, could not be granted to: {}", ownerName, failClientName);
            } catch (Exception e) {
                _logger.warn("Problem acquiring lock: {}", lockName, e);
                Assert.assertNull(e);
            }
        }
        _logger.info("*** acquireDistributedPersistentLock end");
    }

    /**
     * Simulates a client releasing persistent locks.
     * If run standalone, this has nothing to do and is like a NO-OP.
     * When run as part of the DistributedPersistentLockTest test suite, it release locks
     * created by acquireDistributedPersistentLock
     * Repeated NUMCLIENTS times.
     * @throws Exception
     */
    @Test
    public void releaseDistributedPersistentLock() throws Exception {
        _logger.info("*** releaseDistributedPersistentLock start");
        List<DistributedPersistentLock> locks = new ArrayList<DistributedPersistentLock>();
        for(int i=0; i<NUMRUNS; i++) {
            String lockName = String.format("%s-%s", "lock", i);
            try {
                locks.add(connectClient().getPersistentLock(lockName));
            } catch (Exception e) {
                _logger.info(": Problem when instantiating lock {}", lockName, e);
                Assert.assertNull(e);
            }
            _logger.info(": Initialized lock: {}", lockName);
            try {
                _logger.info(": Try releasing lock without checking: {}", lockName);
                locks.get(i).releaseLock("abc");
                String lockOwner = locks.get(i).getLockOwner();
                if(lockOwner != null) {
                    boolean bLockActionResult = locks.get(i).releaseLock(lockOwner);
                    _logger.info(": Released lock: {}; result: {}", lockName, bLockActionResult);
                } else {
                    _logger.warn(": Lock: {} not found. Nothing to release.", lockName);
                }
            } catch (Exception e) {
                _logger.warn("Problem releasing lock: {}", lockName, e);
                Assert.assertNull(e);
            }
        }
        _logger.info("*** releaseDistributedPersistentLock end");
    }

    /**
     * Simulates multiple clients accessing persistent lock API simultaneously.
     * @throws Exception
     */
    @Test
    public void miscDistributedPersistentLock() throws Exception {
        _logger.info("*** miscDistributedPersistentLock start");
        ExecutorService clients = Executors.newFixedThreadPool(NUMCLIENTS);
        for(int i=0; i < NUMCLIENTS; i++) {
            clients.submit(new Runnable() {
                @Override
                public void run() {
                    String lockName = "TestLock";
                    String clientName = Thread.currentThread().getName();
                    String currOwnerName = null;
                    DistributedPersistentLock lock = null;
                    try {
                        lock = connectClient().getPersistentLock(lockName);
                    } catch (Exception e) {
                        _logger.info(": {} miscDistributedPersistentLock could not get coordinator client", e);
                        Assert.assertNull(e);
                    }
                    _logger.info(": ### Client {}, Initialized lock {} ###", clientName, lockName);
                    while(true) {
                        try {
                            _logger.info(": {} ------ client: starts loop ------", clientName);
                            _logger.info(": {} client trying to acquire lock", clientName);
                            Thread.sleep(50);
                            boolean bLockActionResult = lock.acquireLock(clientName);
                            currOwnerName = lock.getLockOwner();
                            _logger.info(": {} is current owner", currOwnerName);
                            Thread.sleep(50);
                            if(bLockActionResult) {
                                _logger.info(": {} request succeeded. doing work", currOwnerName);
                                Thread.sleep(50);
                                _logger.info(": {} work done. releasing lock", currOwnerName);
                                bLockActionResult = lock.releaseLock(clientName);
                                _logger.info(": lock release status: {}; {} released lock", bLockActionResult, clientName);
                                Thread.sleep(100);
                            } else {
                                _logger.info(": {} request failed. retrying.", clientName);
                                Thread.sleep(50);
                            }
                        } catch (InterruptedException e) {
                            //Ignore this.
                        } catch (Exception e) {
                            _logger.info(": {} transient error ...", clientName, e);
                        }
                    }
                }
            });
        }
        clients.awaitTermination(20, TimeUnit.SECONDS);
        _logger.info("*** miscDistributedPersistentLock end");
    }
}
