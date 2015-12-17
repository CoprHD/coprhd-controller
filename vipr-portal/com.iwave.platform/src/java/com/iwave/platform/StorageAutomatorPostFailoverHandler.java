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

public class StorageAutomatorPostFailoverHandler extends DrPostFailoverHandler {
    private static final Logger log = LoggerFactory.getLogger(StorageAutomatorPostFailoverHandler.class);
    @Autowired
    private ModelClient modelClient;

    @Autowired
    private OrderManager orderManager;
    
    public StorageAutomatorPostFailoverHandler() {
    }
    
    protected void execute() {
        ExecutionLog failedLog = new ExecutionLog();
        failedLog.setMessage("DR failover");
        failedLog.setLevel(ExecutionLog.LogLevel.ERROR.name());
        failedLog.setDate(new Date());
        modelClient.save(failedLog);
        
        log.info("Start processing inflight orders - ");
        List<Order> orders = modelClient.orders().findByOrderStatus(OrderStatus.EXECUTING);
        for(Order order : orders) {
            log.info("Fail order for {}", order.getId());
            order.setMessage("DR failover");
            order.setOrderStatus(OrderStatus.ERROR.name());
            orderManager.updateOrder(order);
        }
    }
}
