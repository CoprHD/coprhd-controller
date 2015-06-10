/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
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

package com.emc.storageos.coordinator.client.service.impl;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;

/**
 * Abstract Distributed Queue Consumer Class
 * Note:
 * 1. Each inherited consumer needs to implement the consumeItem();
 * 2. Each inherited consumer who wants to use its own thread pool needs to 
 * override isBusy() to get load balance.
 */
public abstract class DistributedQueueConsumer<T>
{
    private static final Logger _log = LoggerFactory.getLogger(DistributedQueueConsumer.class);

    private final ConcurrentMap<String, DistributedQueueImpl<T>> _distQueueMap 
        = new ConcurrentHashMap<String, DistributedQueueImpl<T>>(); // distributed queue map

    private volatile ThreadPoolExecutor _consumers = null; // default consumer thread pool
    private volatile int _maxThreads = 10;                 // max threads of consumer thread pool
  
    private AtomicInteger _curItems = new AtomicInteger(0);// number of the tasks scheduled

    private AtomicInteger _totalItems = new AtomicInteger(0);// number of total tasks scheduled
    private AtomicLong _totalTimeSpent = new AtomicLong(0);  // total time spent for tasks

    /**
     * Initialize the distributed queue consumer
     *
     * @param queue      queue name
     * @param distQueue  distributed queue instance 
     *                   Some consumers (e.g. Dispather) include several distributed queues.
     * @param maxThreads max threads of consumer thread pool
     */
    void init(String queue, DistributedQueueImpl<T> distQueue, int maxThreads) {
        _maxThreads = maxThreads;
        if(_consumers == null) {
            _consumers = new ThreadPoolExecutor(_maxThreads, _maxThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingDeque<Runnable>());
        }
        _distQueueMap.putIfAbsent(queue, distQueue);
    }

    /**
     * Start to process an item from the distributed queue
     * It would change task count, and schedule it within the consumer thread pool.
     * @param queue    Name of the queue
     * @param itemname Name of the item to process
     * @param item     Data of the item to process
     *
     */
    void startConsumeItem(final String queue, final String itemname, final T item) {
        if (_consumers == null)
            return;

        final int count = _curItems.incrementAndGet();
        _totalItems.incrementAndGet();
        try {
            _consumers.execute(new Runnable() {
                @Override
                public void run() {
                    long startMillis = 0, endMillis = 0;
                    try {
                        startMillis = System.currentTimeMillis();
                        _log.debug("consuming a new task: number of tasks = {}", count);
                        DistributedQueueItemProcessedCallbackImpl itemProcessedCallback =
                                    new DistributedQueueItemProcessedCallbackImpl(queue, itemname);
                        consumeItem(item, itemProcessedCallback);
                    } catch(Exception e) {
                        _log.error("Failed to consume item.", e);
                        _curItems.decrementAndGet(); 
                    } finally {
                        endMillis = System.currentTimeMillis();
                        _totalTimeSpent.getAndAdd(endMillis - startMillis);
                    }
                }
            });
        } catch (Exception e) {
            _log.error("Failed to schedule item.", e);
            _curItems.decrementAndGet(); 
        }
    }

    /**
     * DistributedQueueItemProcessedCallback implementer that calls DistributedQueue's remove method
     * to delete the item with name _itemName, from the queue.
     */
    private class DistributedQueueItemProcessedCallbackImpl implements DistributedQueueItemProcessedCallback {
        private final String _queueName;
        private final String _itemName;

        public DistributedQueueItemProcessedCallbackImpl(String queue, String itemName) {
            _queueName = queue;
            _itemName = itemName;
        }

        @Override
        public void itemProcessed() throws Exception {
            DistributedQueueImpl<T> _distQueue = _distQueueMap.get(_queueName);
            synchronized(_distQueue) {
                _curItems.decrementAndGet(); 
                _distQueue.notifyAll();
            }
            _distQueue.remove(_itemName);
        }

        @Override
        public String toString() {
            return "DistributedQueueItem:"+_itemName;
        } 
    }

    /**
     * uninitialize the consumer
     * shutdown the consumer thread pool.
     *
     * @param timeoutMs time to wait during termination
     *
     * @return true for success, otherwise false
     */
    boolean uninit(long timeoutMs) {
        if (_consumers == null)
            return true;

        try {
            _log.info("total request: {}, time spent: {}", _totalItems.get(), _totalTimeSpent.get());
            _consumers.shutdownNow();
            if (_consumers.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                _log.info("Consumer stopped");
                return true;
            }
            return false;
        } catch (InterruptedException e) {
            _log.warn("Consumer stopping interrupted", e);
            return false;
        }
    }

    /**
     * Check if consumer is busy 
     * Note: 
     * Each inherited consumer who wants to use its own thread pool needs to 
     * override isBusy() to get load balance.
     * @param queue queue path
     *
     * @return true for busy, otherwise false
     */
    public boolean isBusy(String queue) {
        return (_curItems.get() >= _maxThreads)? true:false;
    }

    /**
     * Process an item from the distributed queue
     *
     * @param item Item to process
     * @param callback This must be executed, after the item is processed successfully to remove the item
     *                 from the distributed queue
     *
     * @throws Exception any errors
     */
    public abstract void consumeItem(T item, DistributedQueueItemProcessedCallback callback) throws Exception;
}
