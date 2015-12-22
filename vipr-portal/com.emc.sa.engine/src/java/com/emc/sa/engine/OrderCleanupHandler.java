/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.sa.engine;


import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.coordinator.client.service.DrPostFailoverHandler;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;

/**
 * Dr failover handler for inflight order cleanup. It goes through all EXECUTING orders and kill them. 
 */
public class OrderCleanupHandler extends DrPostFailoverHandler {
    private static final Logger log = LoggerFactory.getLogger(OrderCleanupHandler.class);
    @Autowired
    private ModelClient modelClient;
    @Autowired
    private ExecutionEngineMonitor monitor;
    
    public OrderCleanupHandler() {
    }
    
    @Override
    protected void execute() {
        
        // cleanup enginer monitor 
        getCoordinator().deletePath(ExecutionEngineMonitor.BASE_PATH);
        
        log.info("Start processing inflight orders");
        List<Order> orders = modelClient.orders().findByOrderStatus(OrderStatus.EXECUTING);
        for(Order order : orders) {
            String message = "Order processing terminated because of site failover, order was not completed. " +
                    "You may see partial completion on storage arrays. Check with your administrator to do cleanup if necessary.";
            order.setMessage(message);
            modelClient.save(order);
            monitor.killOrder(order.getId(), message);
        }
    }
}
