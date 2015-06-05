/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.zookeeper;

import org.springframework.stereotype.Component;

/**
 * Wraps a Zookeeper Distributed queue for passing Order requests to the execution engine.
 * 
 * @author dmaddison
 */
@Component
public class OrderExecutionQueue extends GenericQueue<OrderMessage> {
    private static String QUEUE_NAME = "OrderExecutionQueue";

    public OrderExecutionQueue() {
        setName(QUEUE_NAME);
        setWorkThreads(100);
    }
}
