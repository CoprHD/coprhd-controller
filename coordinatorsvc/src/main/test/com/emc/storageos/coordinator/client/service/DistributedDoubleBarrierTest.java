/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.service;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.test.Timing;
import org.apache.curator.utils.CloseableUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.google.common.collect.Lists;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit test cases for com.emc.storageos.coordinator.client.service.DistributedDoubleBarrier, which is based on Apache Curator DistributedDoubleBarrier implementation with 
 * some fixes for CoprHD. We port original unit test cases from Apache Curator DistributedDoubleBarrier and add some new unit tests.
 */
public class DistributedDoubleBarrierTest extends CoordinatorTestBase {
    private static final int           QTY = 5;

    private Logger log = LoggerFactory.getLogger(DistributedDoubleBarrierTest.class);
    
    /**
     * Test return value of enter() in case of timeout
     * 
     * If not all members enter the barrier as expected, it should return false. Subsequent enter() should return false until
     * it meets the barrier criteria   
     * 
     */
    @Test
    public void  testEnterTimeout() throws Exception {
        final Timing            timing = new Timing(2, TimeUnit.SECONDS);
        final AtomicInteger     count = new AtomicInteger(0);
        final ExecutorService         service = Executors.newCachedThreadPool();
        final List<Future<Void>>      futures = Lists.newArrayList();
        for ( int i = 1; i < QTY - 1; ++i )
        {
            Future<Void>    worker = service.submit
                    (
                        new Callable<Void>()
                        {
                            @Override
                            public Void call() throws Exception
                            {
                                ZkConnection zkConnection = createConnection(60 * 1000);
                                CuratorFramework client = zkConnection.curator();
                                try
                                {
                                    zkConnection.connect();
                                    DistributedDoubleBarrier        barrier = new DistributedDoubleBarrier(client, "/barrier/testEnterTimeout", QTY);
                                    log.info("Entering with timeout {} seconds", timing.seconds());
                                    Assert.assertFalse("Return value of enter()", barrier.enter(timing.seconds(), TimeUnit.SECONDS));
                                    count.incrementAndGet();
                                    log.info("Entering again with timeout {} seconds", timing.seconds());
                                    Assert.assertFalse("Return value of enter()", barrier.enter(timing.seconds(), TimeUnit.SECONDS));
                                    count.decrementAndGet();
                                }
                                finally
                                {
                                    CloseableUtils.closeQuietly(client);
                                }
    
                                return null;
                            }
                        }
                    );
            futures.add(worker);
        }
        for ( Future<Void> f : futures )
        {
            f.get();
        }
        Assert.assertEquals("enter/leave count", count.get(), 0);
        
        futures.clear();
        for ( int i = 0; i < QTY; ++i )
        {
            Future<Void>    worker = service.submit
                    (
                        new Callable<Void>()
                        {
                            @Override
                            public Void call() throws Exception
                            {
                                ZkConnection zkConnection = createConnection(60 * 1000);
                                CuratorFramework client = zkConnection.curator();
                                try
                                {
                                    zkConnection.connect();
                                    DistributedDoubleBarrier        barrier = new DistributedDoubleBarrier(client, "/barrier/testEnterTimeout", QTY);
                                    log.info("Entering with timeout {} seconds", timing.seconds());
                                    Assert.assertTrue("Return value of enter()", barrier.enter(timing.seconds(), TimeUnit.SECONDS));
                                    count.incrementAndGet();
                                    log.info("Leaving with timeout {} seconds", timing.seconds());
                                    Assert.assertTrue("Return value of enter()", barrier.leave(timing.seconds(), TimeUnit.SECONDS));
                                    count.decrementAndGet();
                                }
                                finally
                                {
                                    CloseableUtils.closeQuietly(client);
                                }
    
                                return null;
                            }
                        }
                    );
            futures.add(worker);
        }
        for ( Future<Void> f : futures )
        {
            f.get();
        }
        Assert.assertEquals("enter/leave count", count.get(), 0);
    }
    
    /**
     * Test return value of leave() in case of timeout
     *  
     * If any one members doesn't leave correctly, the leave should return false.
     * In this test 5 threads enter a barrier at a time and then the first one leaves within timeout. Another one keeps sleeping.
     * Then Worker1's leave should return false, and all other workers leave with false as well
     */
    @Test
    public void  testLeaveTimeout() throws Exception 
    {
        final Timing            timing = new Timing(2, TimeUnit.SECONDS);
        final AtomicInteger     count = new AtomicInteger(0);
        final ExecutorService         service = Executors.newCachedThreadPool();
        final List<Future<Void>>      futures = Lists.newArrayList();
        Future<Void>    worker1 = service.submit
                (
                    new Callable<Void>()
                    {
                        @Override
                        public Void call() throws Exception
                        {
                            ZkConnection zkConnection = createConnection(60 * 1000);
                            CuratorFramework client = zkConnection.curator();
                            try
                            {
                                zkConnection.connect();
                                DistributedDoubleBarrier        barrier = new DistributedDoubleBarrier(client, "/barrier/testLeaveTimeout", QTY);

                                Assert.assertTrue("Return value of enter() should be true", barrier.enter(timing.seconds(), TimeUnit.SECONDS));
                                count.incrementAndGet();
                                log.info("Leaving with timeout {} seconds", timing.seconds());
                                Assert.assertFalse("Return value of leave() should be false", barrier.leave(timing.seconds(), TimeUnit.SECONDS));
                                log.info("Left with timeout");
                                count.decrementAndGet();
                                Thread.sleep(timing.seconds() * 5 * 1000); // keep the ZK connection until all other workers exit
                            }
                            finally
                            {
                                CloseableUtils.closeQuietly(client);
                            }

                            return null;
                        }
                    }
                );
        futures.add(worker1);
        
        for ( int i = 1; i < QTY; ++i )
        {
            Future<Void>    worker = service.submit
                    (
                        new Callable<Void>()
                        {
                            @Override
                            public Void call() throws Exception
                            {
                                ZkConnection zkConnection = createConnection(60 * 1000);
                                CuratorFramework client = zkConnection.curator();
                                try
                                {
                                    zkConnection.connect();
                                    DistributedDoubleBarrier        barrier = new DistributedDoubleBarrier(client, "/barrier/testLeaveTimeout", QTY);
                                    Assert.assertTrue("Return value of enter() shoud be true", barrier.enter(timing.seconds(), TimeUnit.SECONDS));
                                    count.incrementAndGet();
                                    Thread.sleep(timing.seconds() * 1000);
                                    log.info("Leaving with timeout {} seconds", timing.seconds());
                                    Assert.assertFalse("Return value of leave() should be false", barrier.leave(timing.seconds(), TimeUnit.SECONDS));
                                    count.decrementAndGet();
                                    log.info("Left with timeout");
                                    Thread.sleep(timing.seconds() * 1000); // keep the ZK connection until all other workers exit
                                }
                                finally
                                {
                                    CloseableUtils.closeQuietly(client);
                                }
    
                                return null;
                            }
                        }
                    );
            futures.add(worker);
        }
        for ( Future<Void> f : futures )
        {
            f.get();
        }
        
        Assert.assertEquals("enter/leave count", count.get(), 0);
    }
    
    /**
     * Test case port from Apache Curator 
     */
    @Test
    public void     testMultiClient() throws Exception
    {
        final Timing            timing = new Timing();
        final CountDownLatch    postEnterLatch = new CountDownLatch(QTY);
        final CountDownLatch    postLeaveLatch = new CountDownLatch(QTY);
        final AtomicInteger     count = new AtomicInteger(0);
        final AtomicInteger     max = new AtomicInteger(0);
        List<Future<Void>>      futures = Lists.newArrayList();
        ExecutorService         service = Executors.newCachedThreadPool();
        for ( int i = 0; i < QTY; ++i )
        {
            Future<Void>    future = service.submit
            (
                new Callable<Void>()
                {
                    @Override
                    public Void call() throws Exception
                    {
                        ZkConnection zkConnection = createConnection(60 * 1000);
                        CuratorFramework client = zkConnection.curator();
                        try
                        {
                            zkConnection.connect();
                            DistributedDoubleBarrier        barrier = new DistributedDoubleBarrier(client, "/barrier/testMultiClient", QTY);

                            log.info("Entering with timeout {}", timing.seconds());
                            Assert.assertTrue("Return value of enter()", barrier.enter(timing.seconds(), TimeUnit.SECONDS));
                            log.info("Entered");
                            synchronized(DistributedDoubleBarrierTest.this)
                            {
                                int     thisCount = count.incrementAndGet();
                                if ( thisCount > max.get() )
                                {
                                    max.set(thisCount);
                                }
                            }

                            postEnterLatch.countDown();
                            Assert.assertTrue("postEnterLatch", timing.awaitLatch(postEnterLatch));
                            
                            Assert.assertEquals("entered count", count.get(), QTY);
                            log.info("Leaving timeout {}", timing.seconds());
                            Assert.assertTrue("Return value of leave()", barrier.leave(timing.seconds(), TimeUnit.SECONDS));
                            log.info("Left");
                            count.decrementAndGet();

                            postLeaveLatch.countDown();
                            Assert.assertTrue("postLeaveLatch", timing.awaitLatch(postLeaveLatch));
                        }
                        finally
                        {
                            CloseableUtils.closeQuietly(client);
                        }

                        return null;
                    }
                }
            );
            futures.add(future);
        }

        for ( Future<Void> f : futures )
        {
            f.get();
        }
        Assert.assertEquals(count.get(), 0);
        Assert.assertEquals(max.get(), QTY);
    }

    /**
     * Test case port from Apache Curator 
     */
    @Test
    public void     testOverSubscribed() throws Exception
    {
        final Timing                    timing = new Timing();
        ZkConnection zkConnection = createConnection(10 * 1000);
        CuratorFramework client = zkConnection.curator();
        
        ExecutorService                 service = Executors.newCachedThreadPool();
        ExecutorCompletionService<Void> completionService = new ExecutorCompletionService<Void>(service);
        try
        {
            client.start();

            final Semaphore         semaphore = new Semaphore(0);
            final CountDownLatch    latch = new CountDownLatch(1);
            for ( int i = 0; i < (QTY + 1); ++i )
            {
                completionService.submit
                (
                    new Callable<Void>()
                    {
                        @Override
                        public Void call() throws Exception
                        {
                            DistributedDoubleBarrier        barrier = new DistributedDoubleBarrier(client, "/barrier/testOverSubscribed" , QTY)
                            {
                                @Override
                                protected List<String> getChildrenForEntering() throws Exception
                                {
                                    semaphore.release();
                                    Assert.assertTrue(timing.awaitLatch(latch));
                                    return super.getChildrenForEntering();
                                }
                            };
                            Assert.assertTrue(barrier.enter(timing.seconds(), TimeUnit.SECONDS));
                            Assert.assertTrue(barrier.leave(timing.seconds(), TimeUnit.SECONDS));
                            return null;
                        }
                    }
                );
            }

            Assert.assertTrue(semaphore.tryAcquire(QTY + 1, timing.seconds(), TimeUnit.SECONDS));   // wait until all QTY+1 barriers are trying to enter
            latch.countDown();

            for ( int i = 0; i < (QTY + 1); ++i )
            {
                completionService.take().get(); // to check for assertions
            }
        }
        finally
        {
            service.shutdown();
            CloseableUtils.closeQuietly(client);
        }
    }

    /**
     * Test case port from Apache Curator 
     */
    @Test
    public void     testBasic() throws Exception
    {
        final Timing              timing = new Timing();
        final List<Closeable>     closeables = Lists.newArrayList();
        ZkConnection zkConnection = createConnection(10 * 1000);
        CuratorFramework client = zkConnection.curator();
        try
        {
            closeables.add(client);
            client.start();

            final CountDownLatch    postEnterLatch = new CountDownLatch(QTY);
            final CountDownLatch    postLeaveLatch = new CountDownLatch(QTY);
            final AtomicInteger     count = new AtomicInteger(0);
            final AtomicInteger     max = new AtomicInteger(0);
            List<Future<Void>>      futures = Lists.newArrayList();
            ExecutorService         service = Executors.newCachedThreadPool();
            for ( int i = 0; i < QTY; ++i )
            {
                Future<Void>    future = service.submit
                (
                    new Callable<Void>()
                    {
                        @Override
                        public Void call() throws Exception
                        {
                            DistributedDoubleBarrier        barrier = new DistributedDoubleBarrier(client, "/barrier/testBasic", QTY);

                            Assert.assertTrue(barrier.enter(timing.seconds(), TimeUnit.SECONDS));

                            synchronized(DistributedDoubleBarrierTest.this)
                            {
                                int     thisCount = count.incrementAndGet();
                                if ( thisCount > max.get() )
                                {
                                    max.set(thisCount);
                                }
                            }

                            postEnterLatch.countDown();
                            Assert.assertTrue(timing.awaitLatch(postEnterLatch));

                            Assert.assertEquals(count.get(), QTY);

                            Assert.assertTrue(barrier.leave(10, TimeUnit.SECONDS));
                            count.decrementAndGet();

                            postLeaveLatch.countDown();
                            Assert.assertTrue(timing.awaitLatch(postLeaveLatch));

                            return null;
                        }
                    }
                );
                futures.add(future);
            }

            for ( Future<Void> f : futures )
            {
                f.get();
            }
            Assert.assertEquals(count.get(), 0);
            Assert.assertEquals(max.get(), QTY);
        }
        finally
        {
            for ( Closeable c : closeables )
            {
                CloseableUtils.closeQuietly(c);
            }
        }
    }
}
