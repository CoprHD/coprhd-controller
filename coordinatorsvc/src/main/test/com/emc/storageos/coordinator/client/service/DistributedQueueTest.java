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

import com.emc.storageos.coordinator.exceptions.RetryableCoordinatorException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueImpl;
import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.coordinator.service.impl.CoordinatorImpl;
import com.emc.storageos.coordinator.service.impl.SpringQuorumPeerConfig;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.recipes.queue.QueueSerializer;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.utils.EnsurePath;
import org.junit.Assert;
import org.apache.zookeeper.CreateMode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.lang.Math;

/**
 * DistributedQueue unit test
 */
public class DistributedQueueTest extends CoordinatorTestBase {
    private static final Logger _logger = LoggerFactory.getLogger(DistributedQueueTest.class);
    private static final String QUEUE_NAME = "integerqueue";
    private static final String QUEUE_NAME_2 = "integerqueue2";
    private static final String QUEUE_NAME_3 = "integerqueue3";
    private static final String QUEUE_NAME_4 = "integerqueue4";

    private static List<DistributedQueue<Integer>> statisticDistQueueList = new ArrayList<DistributedQueue<Integer>>();

    /**
     * Queue consumer that keeps track of incoming integers and decrements a latch
     */
    public class IntegerConsumer extends DistributedQueueConsumer<Integer> {
        private CountDownLatch _latch;

        public void setLatch(CountDownLatch latch) {
            _latch = latch;
        }

        @Override
        public void consumeItem(Integer message, DistributedQueueItemProcessedCallback cb) throws Exception {
            cb.itemProcessed();
            _latch.countDown();
        }
    }


    /**
     * Integer serializer
     */
    public class IntegerSerializer implements QueueSerializer<Integer> {
        @Override
        public byte[] serialize(Integer item) {
            return item.toString().getBytes();
        }

        @Override
        public Integer deserialize(byte[] bytes) {
            return Integer.parseInt(new String(bytes));
        }
    }


    /**
     * Deletes given directory
     *
     * @param dir
     */
    public static void cleanDirectory(File dir) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                cleanDirectory(file);
            } else {
                file.delete();
            }
        }
        dir.delete();
    }
   
    /**
     * Tests multiple consumer / producers
     *
     * @throws Exception
     */
    @Test
    public void testDistributedQueue() throws Exception {
        final int eachCount = 1000;
        final int numQueue = 1;
        final int pushThreadCount = 20;

        // manually change 'testlocal' and server for test remote
        // need construct real remote env at first
        boolean testlocal = true;
        CoordinatorClientImpl client = null;
        if (testlocal) {
            client = (CoordinatorClientImpl)connectClient();
        } else {
            List<URI> server = new ArrayList<URI>();
            server.add(URI.create("coordinator://10.247.101.174:2181"));
            server.add(URI.create("coordinator://10.247.101.175:2181"));
            server.add(URI.create("coordinator://10.247.101.176:2181"));
            server.add(URI.create("coordinator://10.247.101.177:2181"));
            server.add(URI.create("coordinator://10.247.101.178:2181"));
            client = (CoordinatorClientImpl)connectClient(server);
        }

        CountDownLatch latch = new CountDownLatch(eachCount * pushThreadCount);
        IntegerConsumer consumer = new IntegerConsumer();
        consumer.setLatch(latch);
        IntegerSerializer serializer = new IntegerSerializer();
        List<DistributedQueue<Integer>> queueList = new ArrayList<DistributedQueue<Integer>>();
        for (int index = 0; index < numQueue; index++) {
            queueList.add(client.getQueue(QUEUE_NAME, consumer, serializer, 25));
        }
        _logger.info("basicTest start");
        for (int index = 0; index < pushThreadCount; index++) {
            startPut(queueList.get(index % numQueue), 0, eachCount);
        }
        Assert.assertTrue(latch.await(600, TimeUnit.SECONDS));
        _logger.info("basicTest end");
        // todo fix me:  wait for async deletes to finish.
        Thread.sleep(1000 * 60);
    }

    @Test
    public void testQueueMax() throws Exception {
        final int maxCount = 100;
        IntegerSerializer serializer = new IntegerSerializer();
        DistributedQueue<Integer> queue = connectClient().getQueue(QUEUE_NAME_2, null, serializer, 25, maxCount);
        try {
            int index = 0;
            for (; index < maxCount * 2; index++) {
                queue.put(index);
            }
            Assert.fail(String.format("Was able to put %1$d items in a queue with %2$d max", index, maxCount));
        } catch (RetryableCoordinatorException e) {
            // expected
            if (e.getServiceCode() == ServiceCode.COORDINATOR_QUEUE_TOO_BUSY) {
                _logger.info("get the expected exception");
                return;
            }
            Assert.fail("receive unexpected exception, sould be QUEUE_TOO_BUSY");
        } catch (Exception e) {
            Assert.fail(String.format("receive unexpected exception: %s", e));
        }
    }

    /**
     * Starts a thread that will put integers [start, end)
     *
     * @param queue destination queue
     * @param start starting value inclusive
     * @param end end value exclusive
     */
    public void startPut(final DistributedQueue<Integer> queue, final int start, final int end) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int index = start; index < end; index++) {
                        queue.put(index);
                    }
                } catch (Exception e) {
                    Assert.assertNull(e);
                }
            }
        }).start();
    }

    /**
     * Queue consumer that for statistic how many item processed
     */
    public class ItemStatisticConsumer extends DistributedQueueConsumer<Integer> {
        private volatile int _serial = 0;
        private AtomicInteger consumedCount = new AtomicInteger(0);

        public void setSerial(int serial) {
            _serial = serial;
        }

        public int getItemConsumed() {
            return consumedCount.get();
        }


        @Override
        public void consumeItem(Integer item, DistributedQueueItemProcessedCallback cb) throws Exception {
            int timeCost = 2;
            Thread.sleep(1000 * timeCost);
            consumedCount.incrementAndGet();
            cb.itemProcessed();
        }
    }

    /**
     * Tests load balance of distributed queue
     *
     * @throws Exception
     */
    @Test
    public void testDistributedQueueLoadBalance() throws Exception {
        // totoally 5 clients, each has only 2 thread
        int clientNumber = 5;
        int threadsPerClient = 2;
        int consumerNumber = clientNumber * threadsPerClient;

        // create distributed queue and all consumers 
        for (int i=0; i < clientNumber-1; i++) {
            spawnStatisticConsumer(i, threadsPerClient);
        }
        DistributedQueue<Integer> queue = createStatisticDistQueue(clientNumber-1, threadsPerClient);
        Assert.assertTrue(queue!=null);

        _logger.info("start to produce items ");
        int itemNumber = 100;
        int timeCost = 2;
        startPut(queue, 0, itemNumber);

        // wait enough time for all items to be consumed.
        // each item needs <timeCost> to be processed.
        int avgConsumed = itemNumber/clientNumber;
        Thread.sleep(1000 * (avgConsumed*timeCost + 10));

        for (int i= 0; i < clientNumber; i++) {
            DistributedQueueImpl<Integer> queueimpl = (DistributedQueueImpl<Integer>)statisticDistQueueList.get(i);
            ItemStatisticConsumer consumerImpl = (ItemStatisticConsumer)queueimpl.getConsumer();
            int count = consumerImpl.getItemConsumed();

            _logger.info("Client " +i+ " finally consumed " + count + " items.");
            // Each client should consume items not far away from average items count.
            Assert.assertTrue(Math.abs(count-avgConsumed)*100/avgConsumed <= 20);
        }
    }

    /**
     * Starts a thread to create a distributed queue for statistic purpose
     * The distqueue has its specific curator client and consumer
     */
    public void spawnStatisticConsumer(final int serial, final int threadsNumber) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DistributedQueue<Integer> queue = createStatisticDistQueue(serial, threadsNumber);

                    Thread.sleep(1000 * 60 * 10);
                } catch(Exception e) {
                    _logger.error("Failed to start client to monitor queue.", e);
                } finally {
                }
            }
        }).start();
    }

    /**
     * Create a distributed queue for statistic purpose
     *
     * @throws Exception
     */
    public DistributedQueue<Integer> createStatisticDistQueue(final int serial, final int threadsNumber) {
        try {
            CoordinatorClientImpl client = null;
            client = (CoordinatorClientImpl)connectClient();

            ItemStatisticConsumer consumer = new ItemStatisticConsumer();
            consumer.setSerial(serial);
            IntegerSerializer serializer = new IntegerSerializer();

            DistributedQueue<Integer> queue = client.getQueue(QUEUE_NAME_4, consumer, serializer, threadsNumber);
            statisticDistQueueList.add(queue);
            return queue;
        } catch(Exception e) {
            _logger.error("Failed to create statistic distribution queue.", e);
            return null;
        }
    }

}
