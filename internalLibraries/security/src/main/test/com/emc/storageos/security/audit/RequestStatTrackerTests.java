/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.audit;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.emc.storageos.security.audit.RequestStatTracker;

import org.junit.Assert;
import org.junit.Test;

public class RequestStatTrackerTests {

    @Test
    public void testNumberRequestsPerMinuteAnd500Errors() throws Exception {

        // 5 threads that increment and decrement the number of active requests during one minute
        // 5 threads that just increment the number of active requests during one minute.
        // 5 threads that increment the number 500 errors.
        // At the end of the test, the numbers of requests and 500 errors should be predictable.

        int threadsIncrDecr = 5;
        int threadsIncrOnly = 5;
        int threads500Errors = 5;
        int allThreads = threadsIncrDecr + threadsIncrOnly + threads500Errors;
        final RequestStatTracker tracker = new RequestStatTracker();
        tracker.init();

        ExecutorService executor = Executors.newFixedThreadPool(allThreads);
        final CountDownLatch waitLatch = new CountDownLatch(allThreads);

        final int numberActiveRequestsPerThread = 500;

        for (int index = 0; index < threadsIncrDecr; index++) {
            executor.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    waitLatch.countDown();
                    waitLatch.await();
                    for (int i = 0; i < numberActiveRequestsPerThread; i++) {
                        tracker.incrementActiveRequests();
                        tracker.decrementActiveRequests();
                    }
                    return null;
                }
            });
        }

        for (int index = 0; index < threadsIncrOnly; index++) {
            executor.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    waitLatch.countDown();
                    waitLatch.await();
                    for (int i = 0; i < numberActiveRequestsPerThread; i++) {
                        tracker.incrementActiveRequests();
                    }
                    return null;
                }
            });
        }

        final int number500ErrorsPerThread = 300;
        for (int index = 0; index < threads500Errors; index++) {
            executor.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    waitLatch.countDown();
                    waitLatch.await();
                    for (int i = 0; i < number500ErrorsPerThread; i++) {
                        tracker.flag500Error();
                    }
                    return null;
                }
            });
        }

        executor.shutdown();

        // there should be 300 errors, 500 active requests.
        Assert.assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        Assert.assertEquals(number500ErrorsPerThread * threads500Errors, tracker.get500Errors());
        Assert.assertEquals(numberActiveRequestsPerThread * threadsIncrOnly, tracker.getActiveRequests());

    }
}
