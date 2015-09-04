package com.emc.storageos.coordinator.client.service.impl;

import com.emc.storageos.services.util.NamedThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Asynchronously consumes tasks from a {@link com.emc.storageos.coordinator.client.service.DistributedLockQueueManager} using a managed
 * thread pool.
 *
 * @author Ian Bibby
 */
public abstract class DistributedLockQueueTaskConsumer<T> {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockQueueTaskConsumer.class);
    private static final int DEFAULT_THREADS = 10;

    private ThreadPoolExecutor consumers;

    public void start() {
        consumers = new NamedThreadPoolExecutor("DLQTaskConsumers", DEFAULT_THREADS);
    }

    public void stop() {
        consumers.shutdownNow();
    }

    public void startConsumeTask(final T task, final DistributedLockQueueTaskConsumerCallback callback) {
        try {
            consumers.execute(new Runnable() {
                @Override
                public void run() {
                    consumeTask(task, callback);
                }
            });
        } catch (Exception e) {
            log.error("Failed to consume task", e);
        }
    }

    public abstract void consumeTask(T task, DistributedLockQueueTaskConsumerCallback callback);
}