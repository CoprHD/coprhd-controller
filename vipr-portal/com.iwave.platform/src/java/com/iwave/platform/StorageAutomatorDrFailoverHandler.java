/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.iwave.platform;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.catalog.OrderManager;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.coordinator.client.service.DrPostFailoverHandler;
import com.emc.storageos.db.client.model.uimodels.ExecutionLog;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;

public class StorageAutomatorDrFailoverHandler extends DrPostFailoverHandler {
    private static final Logger log = LoggerFactory.getLogger(StorageAutomatorDrFailoverHandler.class);
    @Autowired
    private ModelClient modelClient;

    @Autowired
    private OrderManager orderManager;
    
    public StorageAutomatorDrFailoverHandler() {
    }
    
    protected void execute() {
        log.info("DR post failover handler starts");
        ExecutionLog failedLog = new ExecutionLog();
        failedLog.setMessage("Inflight orders are failed because of DR failover");
        failedLog.setLevel(ExecutionLog.LogLevel.ERROR.name());
        failedLog.setDate(new Date());
        modelClient.save(failedLog);
        
        log.info("Start processing inflight orders");
        List<Order> orders = modelClient.orders().findByOrderStatus(OrderStatus.EXECUTING);
        for(Order order : orders) {
            log.info("Fail order for {}", order.getId());
            order.setMessage(String.format("Order %d is failed because of DR failover", order.getOrderNumber()));
            order.setOrderStatus(OrderStatus.ERROR.name());
            orderManager.updateOrder(order);
        }
    }
}
