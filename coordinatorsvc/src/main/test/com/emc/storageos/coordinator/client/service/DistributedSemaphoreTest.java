/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.coordinator.client.service;

import org.apache.curator.framework.recipes.locks.Lease;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * DistributedSemaphoreTest unit test
 */
public class DistributedSemaphoreTest extends CoordinatorTestBase {
    private static final Logger _logger = LoggerFactory.getLogger(DistributedSemaphoreTest.class);
    private static final String SEMAPHORE_NAME_TEST1 = "sampleSemClientTest1";
    private final int POOLSIZE = 20;
    private ExecutorService _workers1 = Executors.newFixedThreadPool(POOLSIZE);
    private static final String SEMAPHORE_NAME_TEST2 = "sampleSemClientTest2";
    private ExecutorService _workers2 = Executors.newFixedThreadPool(POOLSIZE);

    /**
     * Executes multiple workers using the semaphore (but acquiring with infinite wait).
     * If a worker is unable to acquire a lease, it blocks.
     *
     * @throws Exception
     */
    @Test
    public void testDistributedSemaphore() throws Exception {
        final DistributedSemaphore mySem = connectClient().getSemaphore(SEMAPHORE_NAME_TEST1, 1);
        _logger.info("*** DistributedSemaphoreTest start");
        for(int i=0; i<POOLSIZE; i++) {
            _logger.info(": spawning worker number : " + i);
            _workers1.execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 25; i++) {
                        _logger.info(": execution run number : " + i);
                        Lease lease = null;
                        Random rand = new Random();
                        try {
                            _logger.info(": Going to acquire lease.");
                            lease = mySem.acquireLease();
                            _logger.info(": Doing work holding lease : " + lease.toString());
                        } catch (Exception e) {
                            _logger.info(": Problem when acquiring lease or doing work.");
                            Assert.assertNull(e);
                        } finally {
                            if (lease != null) {
                                _logger.info(": Work done .. going to return lease.");
                                try {
                                    mySem.returnLease(lease);
                                    lease = null;
                                    Thread.sleep(rand.nextInt(500));
                                } catch (Exception e) {
                                    _logger.info(": Problem while returning lease: " + lease.toString());
                                    Assert.assertNull(lease);
                                }
                            } else {
                                _logger.info(": No lease to return.");
                            }
                        }
                    }
                }
            });
        }
        _workers1.shutdown();
        Assert.assertTrue(_workers1.awaitTermination(60, TimeUnit.SECONDS));
        _logger.info("*** DistributedSemaphoreTest end");
    }

    /**
     * Executes multiple workers using the semaphore (but acquiring with finite wait).
     * If a worker is unable to acquire a lease within a specified time, it retries.
     *
     * @throws Exception
     */
    @Test
    public void testDistributedSemaphoreFiniteWait() throws Exception {
        final DistributedSemaphore mySem = connectClient().getSemaphore(SEMAPHORE_NAME_TEST2, 1);
        _logger.info("*** DistributedSemaphoreFiniteWaitTest start");
        for(int i=0; i<POOLSIZE; i++) {
            _logger.info(": spawning worker number : " + i);
            _workers2.execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 25; i++) {
                        _logger.info(": execution run number : " + i);
                        Lease lease = null;
                        Random rand = new Random();
                        try {
                            _logger.info(": Going to acquire lease.");
                            lease = mySem.acquireLease(50, TimeUnit.MILLISECONDS);
                            if(lease == null) {
                                _logger.info(": Could not acquire lease.");
                                Thread.sleep(rand.nextInt(500));
                            } else {
                                _logger.info(": Doing work holding lease : " + lease.toString());
                            }
                        } catch (Exception e) {
                            _logger.info(": Problem when acquiring lease or doing work.");
                            Assert.assertNull(e);
                        } finally {
                            if(lease != null) {
                                _logger.info(": Work done .. going to return lease.");
                                try {
                                    mySem.returnLease(lease);
                                    lease = null;
                                } catch(Exception e)  {
                                    _logger.info(": Problem while returning lease: " + lease.toString());
                                    Assert.assertNull(lease);
                                }
                            } else {
                                _logger.info(": No lease to return.");
                            }
                        }
                    }
                }
            });
        }
        _workers2.shutdown();
        Assert.assertTrue(_workers2.awaitTermination(60, TimeUnit.SECONDS));
        _logger.info("*** DistributedSemaphoreFiniteWaitTest end");
    }
}
