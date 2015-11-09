/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.common;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedQueue;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;

public class JobProducer {
    private static final Logger log = LoggerFactory.getLogger(JobProducer.class);
    public static final String QUEUE_NAME = "syssvc";
    public static final int DEFAULT_MAX_THREADS = 1;
    public static final long DEFAULT_MAX_WAIT_STOP = 60 * 1000;
    private DistributedQueue<Serializable> queue;

    private CoordinatorClient coordinator;
    private DistributedQueueConsumer<Serializable> consumer;

    /**
     * Start db consistency job queue
     */
    public void startQueue() {
        log.info("Starting job queue");
        try {
            queue = coordinator.getQueue(QUEUE_NAME, consumer, new JobSerializer(), DEFAULT_MAX_THREADS);
        } catch (Exception e) {
            log.error("can not startup job queue", e);
        }
    }
    
    /**
     * Stop db consistency job queue
     */
    public void stopQueue() {
        log.info("Stopping job queue");
        queue.stop(DEFAULT_MAX_WAIT_STOP);
    }

    public void enqueue(Serializable job) {
        log.info("enqueue job:{}", job);
        try {
            queue.put(job);
        } catch (Exception e) {
            log.error("fail to enqueue job", e);
            throw new RuntimeException(e);
        }
    }
    
    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public DistributedQueueConsumer<Serializable> getConsumer() {
        return consumer;
    }

    public void setConsumer(DistributedQueueConsumer<Serializable> consumer) {
        this.consumer = consumer;
    }
}
