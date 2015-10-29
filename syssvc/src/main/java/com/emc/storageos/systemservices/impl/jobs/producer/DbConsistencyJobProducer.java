/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedQueue;
import com.emc.storageos.systemservices.impl.jobs.DbConsistencyJob;
import com.emc.storageos.systemservices.impl.jobs.DbConsistencyJobSerializer;
import com.emc.storageos.systemservices.impl.jobs.consumer.DbConsistencyJobConsumer;

public class DbConsistencyJobProducer {
    private static final Logger log = LoggerFactory.getLogger(DbConsistencyJobProducer.class);
    public static final String QUEUE_NAME = "dbconsistencyservice";
    public static final int DEFAULT_MAX_THREADS = 1;
    public static final long DEFAULT_MAX_WAIT_STOP = 60 * 1000;
    
    private CoordinatorClient coordinator;
    private DistributedQueue<DbConsistencyJob> queue;
    private DbConsistencyJobConsumer consumer;
    
    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }
    
    /**
     * Start db consistency job queue
     */
    public void startQueue() {
        log.info("Starting db consistency job queue");
        try {
            queue = coordinator.getQueue(QUEUE_NAME, consumer, new DbConsistencyJobSerializer(), DEFAULT_MAX_THREADS);
        } catch (Exception e) {
            log.error("can not startup db consistency job queue", e);
        }
    }
    
    /**
     * Stop db consistency job queue
     */
    public void stopQueue() {
        log.info("Stopping db consistency job queue");
        queue.stop(DEFAULT_MAX_WAIT_STOP);
    }

    public void enqueue(DbConsistencyJob job) {
        log.info("enqueue job:{}", job);
        try {
            queue.put(job);
        } catch (Exception e) {
            log.error("fail to enqueue job", e);
            throw new RuntimeException(e);
        }
    }
}
