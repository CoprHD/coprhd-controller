/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.catalog;

import java.net.URI;
import java.util.Date;
import java.util.List;

import com.emc.storageos.db.client.model.uimodels.ExecutionLog;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.ExecutionTaskLog;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderAndParams;
import com.emc.storageos.db.client.model.uimodels.OrderParameter;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.storageos.security.authentication.StorageOSUser;

public interface OrderManager {

    public Order getOrderById(URI id);

    public Order createOrder(Order order, List<OrderParameter> orderParameters, StorageOSUser user);

    public void createOrderParameter(OrderParameter orderParameter);

    public void updateOrder(Order order);

    public void deleteOrder(Order order);

    public void pauseOrder(Order order);

    public void resumeOrder(Order order);

    public List<Order> getOrders(URI tenantId);

    public List<Order> getUserOrders(StorageOSUser user);

    public List<Order> findOrdersByStatus(URI tenantId, OrderStatus orderStatus);

    public List<Order> findOrdersByTimeRange(URI tenantId, Date startTime, Date endTime);

    public List<ExecutionLog> getOrderExecutionLogs(Order order);

    public List<ExecutionTaskLog> getOrderExecutionTaskLogs(Order order);

    public ExecutionState getOrderExecutionState(URI executionStateId);

    public List<OrderParameter> getOrderParameters(URI orderId);

    public List<OrderAndParams> getOrdersAndParams(List<URI> ids);

    public String getNextOrderNumber();

    public void processOrder(Order order);

}
