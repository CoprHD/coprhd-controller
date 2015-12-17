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
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.ExecutionStatus;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;

public class StorageAutomatorOrderCleanupHandler extends DrPostFailoverHandler {
    private static final Logger log = LoggerFactory.getLogger(StorageAutomatorOrderCleanupHandler.class);
    @Autowired
    private ModelClient modelClient;
    @Autowired
    private OrderManager orderManager;
    
    public StorageAutomatorOrderCleanupHandler() {
    }
    
    protected void execute() {
        log.info("Start processing inflight orders");
        List<Order> orders = modelClient.orders().findByOrderStatus(OrderStatus.EXECUTING);
        for(Order order : orders) {
            log.info("Fail order for {}", order.getId());
            failOrder(order);
        }
    }
    
    private void failOrder(Order order) {
        ExecutionLog failedLog = new ExecutionLog();
        failedLog.setMessage("Terminated due to site failover from a system disaster");
        failedLog.setLevel(ExecutionLog.LogLevel.ERROR.name());
        failedLog.setDate(new Date());
        modelClient.save(failedLog);
        
        ExecutionState state = modelClient.executionStates().findById(order.getExecutionStateId());
        state.addExecutionLog(failedLog);
        state.setEndDate(new Date());
        state.setExecutionStatus(ExecutionStatus.FAILED.name());
        modelClient.save(state);
        
        order.setMessage("Terminated due to site failover from a system disaster");
        order.setOrderStatus(OrderStatus.ERROR.name());
        orderManager.updateOrder(order);
    }
}
