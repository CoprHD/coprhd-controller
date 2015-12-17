/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.zookeeper;

import java.util.Arrays;

import org.springframework.stereotype.Component;

import com.emc.storageos.coordinator.client.service.DrPostFailoverHandler.QueueCleanupHandler;

/**
 * Wraps a Zookeeper Distributed queue for passing Order requests to the execution engine.
 * 
 * @author dmaddison
 */
@Component
public class OrderExecutionQueue extends GenericQueue<OrderMessage> {
    public static String QUEUE_NAME = "OrderExecutionQueue";
    
    public OrderExecutionQueue() {
        setName(QUEUE_NAME);
        setWorkThreads(100);
    }
    
    public void start() {
        QueueCleanupHandler drPostFailoverHandler = new QueueCleanupHandler("OrderQueueHandler");
        drPostFailoverHandler.setQueueNames(Arrays.asList(QUEUE_NAME));
        drPostFailoverHandler.run();
        
        super.start();
    }
}
