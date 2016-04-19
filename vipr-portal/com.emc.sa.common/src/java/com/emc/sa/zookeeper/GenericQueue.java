/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.zookeeper;

import java.io.IOException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedQueue;
import com.emc.storageos.coordinator.client.service.DrPostFailoverHandler.QueueCleanupHandler;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.coordinator.client.service.impl.GenericSerializer;
import org.apache.curator.framework.recipes.queue.QueueSerializer;

public class GenericQueue<T> {
    private static final Logger LOG = Logger.getLogger(GenericQueue.class);
    private static int DEFAULT_WORK_THREADS = 10;

    private String name;
    private int workThreads = DEFAULT_WORK_THREADS;
    @Autowired
    private CoordinatorClient coordinatorClient;
    @Autowired
    private QueueCleanupHandler drQueueCleanupHandler;
    
    private DistributedQueue<T> queue;
    
    public void setWorkThreads(int workThreads) {
        this.workThreads = workThreads;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    @PostConstruct
    public void start() {
        LOG.info("Starting queue: " + name);
        if (!coordinatorClient.isConnected()) {
            try {
                LOG.info("Starting coordinator for: " + name);
                coordinatorClient.start();
            } catch (IOException e) {
                throw new RuntimeException("Error Starting Coordinator Client", e);
            }
        }
        drQueueCleanupHandler.run();
    }

    @PreDestroy
    public void stop() {
        // Queue could be null if neither listenForRequests or putItem has been called
        if (queue != null) {
            queue.stop(0);
        }
    }

    public void listenForRequests(DistributedQueueConsumer<T> listener) throws Exception {
        LOG.info("Listening for requests on: " + name);
        queue = coordinatorClient.getQueue(name, listener, new Serializer<T>(), workThreads);
    }

    public void putItem(T item) throws Exception {
        if (queue == null) {
            LOG.info("Creating queue: " + name);
            queue = coordinatorClient.getQueue(name, null, new Serializer<T>(), workThreads);
        }
        LOG.info("Adding item to queue: " + name);
        queue.put(item);
    }

    private static class Serializer<T> implements QueueSerializer<T> {
        @Override
        public byte[] serialize(T item) {
            return GenericSerializer.serialize(item);
        }

        @Override
        @SuppressWarnings("unchecked")
        public T deserialize(byte[] bytes) {
            return (T) GenericSerializer.deserialize(bytes);
        }
    }
}
