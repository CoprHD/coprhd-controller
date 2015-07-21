/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.suite;

import java.net.URI;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import com.emc.sa.model.BaseModelTest;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.ExecutionLog;
import com.emc.storageos.db.client.model.uimodels.ExecutionLog.LogLevel;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.ExecutionStatus;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindow;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.NamedURI;

public class OrderTest extends BaseModelTest<Order> {

    private static final Logger _logger = Logger.getLogger(OrderTest.class);
    
    public OrderTest() {
        super(Order.class);
    }
    
    @Test
    public void testPersistObject() throws Exception {
        _logger.info("Starting persist Order test");

        Order model = new Order();
        model.setLabel("foo");
        URI catalogServiceId = URIUtil.createId(CatalogService.class);
        model.setCatalogServiceId(catalogServiceId);
        Date d = new Date();
        model.setDateCompleted(d);
        URI executionStateId = URIUtil.createId(ExecutionState.class);
        model.setExecutionStateId(executionStateId);
        model.setMessage("my message");
        model.setOrderStatus(OrderStatus.CANCELLED.name()); 
        model.setSummary("my summary");
        model.setSubmittedByUserId("my user");
        model.setTenant(DEFAULT_TENANT);
        
        save(model);
        model = findById(model.getId());
        
        Assert.assertNotNull(model);
        Assert.assertEquals("foo", model.getLabel());
        Assert.assertEquals(catalogServiceId, model.getCatalogServiceId());
        Assert.assertEquals(d, model.getDateCompleted());
        Assert.assertEquals(executionStateId, model.getExecutionStateId());
        Assert.assertEquals("my message", model.getMessage());
        Assert.assertEquals(OrderStatus.CANCELLED.name(), model.getOrderStatus());
        Assert.assertEquals("my user", model.getSubmittedByUserId());
        Assert.assertEquals("my summary", model.getSummary());
        Assert.assertEquals(DEFAULT_TENANT, model.getTenant());
        
    }
    
    @Test
    public void testSaveOrderAndExecutionState() throws Exception {
        _logger.info("Starting save Order and Execution State test");

        ModelClient modelClient = getModelClient();

        Order model = new Order();
        model.setLabel("foo");
        URI catalogServiceId = new URI("urn:CatalogService:1");
        model.setCatalogServiceId(catalogServiceId);
        Date d = new Date();
        model.setDateCompleted(d);
        model.setMessage("my message");
        model.setOrderStatus(OrderStatus.CANCELLED.name());
        model.setSubmittedByUserId("urn:User:1");
        model.setSummary("my summary");

        ExecutionState executionState = new ExecutionState();
        executionState.setExecutionStatus(ExecutionStatus.NONE.name());
        executionState.setStartDate(d);
        executionState.setCurrentTask("my task");
        modelClient.save(executionState);
        
        model.setExecutionStateId(executionState.getId());
        
        ExecutionLog executionLog1 = new ExecutionLog();
        executionLog1.setLevel(LogLevel.DEBUG.name());
        executionLog1.setMessage("my message 1");
        modelClient.save(executionLog1);
        
        executionState.addExecutionLog(executionLog1);
        
        ExecutionLog executionLog2 = new ExecutionLog();
        executionLog2.setLevel(LogLevel.ERROR.name());
        executionLog2.setMessage("my message 2");
        modelClient.save(executionLog2);
        
        executionState.addExecutionLog(executionLog2);   
        modelClient.save(executionState);

        save(model);
        Order order = findById(model.getId());

        Assert.assertNotNull(order);
        Assert.assertEquals("foo", order.getLabel());
        Assert.assertEquals(catalogServiceId, order.getCatalogServiceId());
        Assert.assertEquals(d, order.getDateCompleted());
        Assert.assertEquals(executionState.getId(), order.getExecutionStateId());
        Assert.assertEquals("my message", order.getMessage());
        Assert.assertEquals(OrderStatus.CANCELLED.name(), order.getOrderStatus());
        Assert.assertEquals("urn:User:1", order.getSubmittedByUserId());
        Assert.assertEquals("my summary", order.getSummary());
        
        ExecutionState exeState = modelClient.executionStates().findById(order.getExecutionStateId());
        Assert.assertNotNull(exeState);
        Assert.assertEquals(ExecutionStatus.NONE.name(), exeState.getExecutionStatus());
        Assert.assertEquals(d, exeState.getStartDate());
        Assert.assertEquals(2, exeState.getLogIds().size());
        
        List<ExecutionLog> logs = modelClient.executionLogs().findByIds(exeState.getLogIds());
        Assert.assertNotNull(logs);
        Assert.assertEquals(2, logs.size());
        
    }    
    
    @Test
    public void testFindScheduledByExecutionWindow() {
        _logger.info("Starting save Order and Execution State test");

        ModelClient modelClient = getModelClient();

        NamedURI ewId1 = new NamedURI(URIUtil.createId(ExecutionWindow.class), "ewId1");
        NamedURI ewId2 = new NamedURI(URIUtil.createId(ExecutionWindow.class), "ewId2");
        
        Order o1 = createOrder(OrderStatus.PENDING);
        o1.setExecutionWindowId(ewId1);        
        modelClient.save(o1);        

        Order o2 = createOrder(OrderStatus.SCHEDULED);
        o2.setExecutionWindowId(ewId2);
        modelClient.save(o2);

        Order o3 = createOrder(OrderStatus.SCHEDULED);
        o3.setExecutionWindowId(ewId1);
        modelClient.save(o3);        
        
        List<Order> orders = modelClient.orders().findScheduledByExecutionWindow(ewId2.getURI().toString());
        Assert.assertNotNull(orders);
        Assert.assertEquals(1, orders.size());

        orders = modelClient.orders().findScheduledByExecutionWindow(ewId1.getURI().toString());
        Assert.assertNotNull(orders);
        Assert.assertEquals(1, orders.size());        
        
    }
    
    @Test
    public void testFindByOrderStatus() {
        _logger.info("Starting findByOrderStatus test");

        ModelClient modelClient = getModelClient();
        
        Order o1 = create("t1", OrderStatus.APPROVAL);
        modelClient.save(o1);

        Order o2 = create("t1", OrderStatus.APPROVAL);
        modelClient.save(o2);
        
        Order o3 = create("t1", OrderStatus.PENDING);
        modelClient.save(o3);        
        
        Order o4 = create("t2", OrderStatus.APPROVAL);
        modelClient.save(o4);
        
        Order o5 = create("t2", OrderStatus.PENDING);
        modelClient.save(o5);
        
        Order o6 = create("t3", OrderStatus.PENDING);
        modelClient.save(o6);                
        
        List<Order> orders = modelClient.orders().findByOrderStatus("t1", OrderStatus.APPROVAL);
        Assert.assertNotNull(orders);
        Assert.assertEquals(2, orders.size());
        
        orders = modelClient.orders().findByOrderStatus("t1", OrderStatus.PENDING);
        Assert.assertNotNull(orders);
        Assert.assertEquals(1, orders.size());
        
        orders = modelClient.orders().findByOrderStatus("t1", OrderStatus.SCHEDULED);
        Assert.assertNotNull(orders);
        Assert.assertEquals(0, orders.size());
        
        orders = modelClient.orders().findByOrderStatus("t2", OrderStatus.APPROVAL);
        Assert.assertNotNull(orders);
        Assert.assertEquals(1, orders.size());
        
        orders = modelClient.orders().findByOrderStatus("t2", OrderStatus.PENDING);
        Assert.assertNotNull(orders);
        Assert.assertEquals(1, orders.size());        
        
        orders = modelClient.orders().findByOrderStatus("t2", OrderStatus.SCHEDULED);
        Assert.assertNotNull(orders);
        Assert.assertEquals(0, orders.size());    
        
        orders = modelClient.orders().findByOrderStatus("t3", OrderStatus.APPROVAL);
        Assert.assertNotNull(orders);
        Assert.assertEquals(0, orders.size());
        
        orders = modelClient.orders().findByOrderStatus("t3", OrderStatus.PENDING);
        Assert.assertNotNull(orders);
        Assert.assertEquals(1, orders.size());        
        
        orders = modelClient.orders().findByOrderStatus("t3", OrderStatus.SCHEDULED);
        Assert.assertNotNull(orders);
        Assert.assertEquals(0, orders.size());        
        
    }
    
    private static Order createOrder(OrderStatus status) {
        return create(DEFAULT_TENANT, status);
    }
    
    private static Order create(String tenant, OrderStatus status) {
        Order model = new Order();
        model.setLabel("foo");
        URI catalogServiceId = URIUtil.createId(CatalogService.class);
        model.setCatalogServiceId(catalogServiceId);
        Date d = new Date();
        model.setDateCompleted(d);
        model.setMessage("my message");
        model.setOrderStatus(status.name());
        model.setSubmittedByUserId("urn:User:1");
        model.setSummary("my summary");
        model.setTenant(tenant);
        return model;        
    }
    
}
