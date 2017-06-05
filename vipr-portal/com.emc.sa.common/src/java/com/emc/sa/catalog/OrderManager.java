/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.catalog;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.model.uimodels.*;
import com.emc.storageos.security.authentication.StorageOSUser;

public interface OrderManager {

    public Order getOrderById(URI id);

    public Order createOrder(Order order, List<OrderParameter> orderParameters, StorageOSUser user);

    public void createOrderParameter(OrderParameter orderParameter);

    public void updateOrder(Order order);

    public void canBeDeleted(Order order, OrderStatus status);

    public void cancelOrder(Order order);

    public void deleteOrder(Order order);

    public List<Order> getOrders(URI tenantId);

    public List<Order> getUserOrders(StorageOSUser user, long startTime, long endTime, int maxCount);

    public long getOrderCount(StorageOSUser user, long startTime, long endTime);

    public Map<String, Long> getOrderCount(List<URI> tids, long startTime, long endTime);

    public List<Order> findOrdersByStatus(URI tenantId, OrderStatus orderStatus);

    public List<Order> findOrdersByTimeRange(URI tenantId, Date startTime, Date endTime, int maxCount);

    public List<ExecutionLog> getOrderExecutionLogs(Order order);

    public List<ExecutionTaskLog> getOrderExecutionTaskLogs(Order order);

    public ExecutionState getOrderExecutionState(URI executionStateId);

    public List<OrderParameter> getOrderParameters(URI orderId);

    public List<OrderAndParams> getOrdersAndParams(List<URI> ids);

    public String getNextOrderNumber();

    public void processOrder(Order order);

}
