/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.catalog;

import static com.emc.storageos.db.client.URIUtil.uri;

import org.apache.log4j.Logger;

import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.sa.zookeeper.OrderMessage;
import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;

public class OrderCompletionConsumer extends DistributedQueueConsumer<OrderMessage> {

    private static final Logger log = Logger.getLogger(OrderCompletionConsumer.class);

    private OrderManagerImpl orderManager;

    public OrderCompletionConsumer(OrderManagerImpl orderManager) {
        this.orderManager = orderManager;
    }

    @Override
    public void consumeItem(OrderMessage message, DistributedQueueItemProcessedCallback callback) throws Exception {
        try {
            log.info("Order completed: " + message.getOrderId());
            Order order = orderManager.getOrderById(uri(message.getOrderId()));
            orderManager.processOrder(order);
        } catch (Exception e) {
            log.error("Failed to process order completion notification", e);
        } finally {
            callback.itemProcessed();
        }
    }

}
