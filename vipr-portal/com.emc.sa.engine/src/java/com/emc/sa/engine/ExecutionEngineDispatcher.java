/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine;

import javax.annotation.PostConstruct;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.zookeeper.OrderCompletionQueue;
import com.emc.sa.zookeeper.OrderExecutionQueue;
import com.emc.sa.zookeeper.OrderMessage;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;

/**
 * Takes Order Execution Requests off the queue and dispatches them to the Execution Engine
 * 
 * @author dmaddison
 */
@Component
public class ExecutionEngineDispatcher {
    private static Logger LOG = Logger.getLogger(ExecutionEngineDispatcher.class);
    @Autowired
    private OrderExecutionQueue executionQueue;
    @Autowired
    private OrderCompletionQueue completionQueue;
    @Autowired
    private ModelClient modelClient;
    @Autowired
    private ExecutionEngine executionEngine;
    @Autowired
    private ExecutionEngineMonitor monitor;

    @PostConstruct
    public void start() throws Exception {
        executionQueue.listenForRequests(new Consumer());
    }

    public void processOrder(Order order) {
        try {
            monitor.addOrder(order);
            executionEngine.executeOrder(order);
            monitor.removeOrder(order);
            notifyCompletion(order);
        }
        catch (Exception e) {
            String message = String.format("Error processing order %s [%s]", order.getOrderNumber(), order.getId());
            LOG.error(message, e);
            tryMarkOrderAsFailed(order, e);
            throw e;
        }
    }

    private void tryMarkOrderAsFailed(Order order, Throwable cause) {
        try {
            order.setOrderStatus(OrderStatus.ERROR.name());
            order.setMessage(ExceptionUtils.getFullStackTrace(cause));
            if (modelClient != null) {
                modelClient.save(order);
            }
        }
        catch (RuntimeException e) {
            // Nothing we can do at this point, simply log it
            String message = String
                    .format("Could update state of Order %s [%s]", order.getOrderNumber(), order.getId());
            LOG.error(message, e);
        }
    }

    protected void notifyCompletion(Order order) {
        String orderId = order.getId().toString();
        try {
            completionQueue.putItem(new OrderMessage(orderId));
        }
        catch (Exception e) {
            LOG.error("Failed to notify of order completion for order: " + orderId, e);
        }
    }

    public class Consumer extends DistributedQueueConsumer<OrderMessage> {
        @Override
        public void consumeItem(OrderMessage message, DistributedQueueItemProcessedCallback callback) throws Exception {
            Order order = getOrder(message);
            LOG.info(String.format("Consuming Order %s [%s]", order.getOrderNumber(), order.getId()));
            callback.itemProcessed();
            processOrder(order);
        }

        private Order getOrder(OrderMessage message) {
            Order order = modelClient.orders().findById(message.getOrderId());
            if (order == null) {
                throw new IllegalArgumentException("Order not found: " + message.getOrderId());
            }
            return order;
        }
    }

    public void setModelClient(ModelClient modelClient) {
        this.modelClient = modelClient;
    }

    public void setExecutionQueue(OrderExecutionQueue executionQueue) {
        this.executionQueue = executionQueue;
    }

    public void setCompletionQueue(OrderCompletionQueue completionQueue) {
        this.completionQueue = completionQueue;
    }

    public void setExecutionEngine(ExecutionEngine executionEngine) {
        this.executionEngine = executionEngine;
    }
}
