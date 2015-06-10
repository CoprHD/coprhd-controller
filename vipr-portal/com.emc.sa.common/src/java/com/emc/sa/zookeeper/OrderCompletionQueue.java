/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.zookeeper;

import org.springframework.stereotype.Component;

@Component
public class OrderCompletionQueue extends GenericQueue<OrderMessage> {
    private static String QUEUE_NAME = "OrderCompletionQueue";

    public OrderCompletionQueue() {
        setName(QUEUE_NAME);
    }
}
