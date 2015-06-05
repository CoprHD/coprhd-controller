package com.emc.sa.zookeeper;

import com.emc.sa.util.TestCoordinatorService;
import com.emc.sa.util.TestDbService;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;

import static junit.framework.Assert.assertEquals;

/**
 * @author dmaddison
 */
public class OrderNumberSequenceTests {
    private Logger LOG = Logger.getLogger(OrderNumberSequenceImpl.class);

    private TestCoordinatorService coordinatorService;
    private TestDbService cassandraDB;

    private OrderNumberSequenceImpl orderNumberSequence;

    @Before
    public void setup() throws Exception {
        coordinatorService = new TestCoordinatorService();
        coordinatorService.startClean();

        cassandraDB = new TestDbService(coordinatorService);
        cassandraDB.startClean();
    }

    @Ignore("Removed from automatic testing so that Cassandra will allow the test to run")
    @Test
    public void singleThreadBasicTest() throws Exception {

        OrderNumberSequenceImpl orderNumberSequence = new OrderNumberSequenceImpl();
        orderNumberSequence.setCoordinatorClient(coordinatorService.getCoordinatorClient());
        orderNumberSequence.start();

        assertEquals(1,orderNumberSequence.nextOrderNumber());
        assertEquals(2,orderNumberSequence.nextOrderNumber());
        assertEquals(3,orderNumberSequence.nextOrderNumber());
        assertEquals(4,orderNumberSequence.nextOrderNumber());
    }

    @Ignore("Removed because TestDbService doesn't work anymore")
    @Test
    public void multipleThreadStressTest() throws Exception {
        int NUMBER_OF_THREADS = 20;
        final int NUMBER_OF_ORDERS = 20;

        final OrderNumberSequenceImpl orderNumberSequence = new OrderNumberSequenceImpl();
        orderNumberSequence.setCoordinatorClient(coordinatorService.getCoordinatorClient());
        orderNumberSequence.start();

        final CountDownLatch startThreadsLatch = new CountDownLatch(1);
        final CountDownLatch threadsEndedLatch = new CountDownLatch(NUMBER_OF_THREADS);

        final Set<Long> orderNumbers = new ConcurrentSkipListSet<Long>();
        for (int i=0;i<NUMBER_OF_THREADS;i++) {
            new Thread(new Runnable() {
                public void run() {
                try {
                    startThreadsLatch.await();

                    for (int i = 0; i < NUMBER_OF_ORDERS; i++) {
                        Long orderNumber = orderNumberSequence.nextOrderNumber();
                        LOG.info(Thread.currentThread().getName() + " Order Number = " + orderNumber);
                        orderNumbers.add(orderNumber);

                        Thread.sleep(200);
                    }
                    threadsEndedLatch.countDown();
                }
                catch (InterruptedException e) {
                    LOG.error(e);
                }
                }
            }).start();
        }

        // Start threads
        startThreadsLatch.countDown();

        // Wait for threads to finish
        threadsEndedLatch.await();

        // This works because each thread is storing the order number it gets in a SET (which will de-dupe)
        assertEquals(NUMBER_OF_THREADS * NUMBER_OF_ORDERS, orderNumbers.size());
    }

    @After
    public void tearDown() throws Exception {
        cassandraDB.destroy();
        coordinatorService.stop();
    }
}
