/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.service;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public class DistributedDoubleBarrierTest extends CoordinatorTestBase {
    private Logger log = LoggerFactory.getLogger(DistributedDoubleBarrierTest.class);
    private String barrierPath = "/barriers/test";

    /**
     * If not all members leave correctly, the leave should return false.
     * In this test 2 threads enter a barrier at a time and then the first one leaves within timeout. Another one keeps sleeping.
     * Then Worker1's leave should return false.
     */
    @Test
    public void testTimeout() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        Future<Boolean> result1 = executor.submit(new Worker1("worker1"));

        ExecutorService executor2 = Executors.newFixedThreadPool(1);
        Future<Boolean> result2 = executor2.submit(new Worker2("worker2"));

        while (true) {
            if (result1.isDone()) {
                Assert.assertFalse("Work1 should return false since it leaves due to timeout", result1.get());
                break;
            }
        }

        while (true) {
            if (result2.isDone()) {
                Assert.assertFalse("Work2 should return false since work1 leaves with timeout", result2.get());
                break;
            }
        }

        log.info("Testing done");
    }

    class Worker1 implements Callable<Boolean> {
        private final String name;

        public Worker1(String name) {
            this.name = name;
        }

        @Override
        public Boolean call() throws Exception {
            Thread.currentThread().setName(name);
            try {
                DistributedDoubleBarrier barrier = connectClient().getDistributedDoubleBarrier(barrierPath, 2);
                log.info("{} entering", name);
                boolean allEntered = barrier.enter(3, TimeUnit.SECONDS);
                log.info("{} entered with {}", name, allEntered);
                
                log.info("sleeping 2 sec to wait for work2 entered");
                Thread.sleep(2 * 1000);
                
                log.info("{} leaving", name);
                boolean allLeft = barrier.leave(3, TimeUnit.SECONDS);
                log.info("{} left with {}", name, allLeft);

                return allLeft;
            } catch (Exception e) {
                log.error("Error in worker1", e);
                throw e;
            } finally {
            }
        }
    }

    class Worker2 implements Callable<Boolean> {
        private final String name;

        public Worker2(String name) {
            this.name = name;
        }

        @Override
        public Boolean call() throws Exception {
            Thread.currentThread().setName(name);

            try {
                DistributedDoubleBarrier barrier = connectClient().getDistributedDoubleBarrier(barrierPath, 2);
                log.info("{} entering", name);
                boolean allEntered = barrier.enter(3, TimeUnit.SECONDS);
                log.info("{} entered with {}", name, allEntered);

                log.info("sleeping 10 sec");
                Thread.sleep(10 * 1000);

                log.info("{} leaving", name);
                boolean allLeft = barrier.leave(3, TimeUnit.SECONDS);
                log.info("{} left with {}", name, allLeft);

                return allLeft;
            } catch (Exception e) {
                log.error("Error in worker1", e);
                throw e;
            } finally {
            }
        }
    }
}
