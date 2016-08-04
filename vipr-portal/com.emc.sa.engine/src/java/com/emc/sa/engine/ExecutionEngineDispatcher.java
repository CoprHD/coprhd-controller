/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine;

import java.net.URI;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.eclipse.jetty.util.log.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.model.uimodels.EphemeralObject;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.storageos.db.modelclient.model.BlockSnapshot;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.vipr.client.AuthClient;
import com.emc.vipr.client.ClientConfig;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.sa.model.dao.ModelClient;

import com.emc.sa.zookeeper.OrderCompletionQueue;
import com.emc.sa.zookeeper.OrderExecutionQueue;
import com.emc.sa.zookeeper.OrderMessage;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
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

    @Autowired
    private ClientConfig clientConfig;
    @Autowired
    @Qualifier("encryptionProvider")
    private EncryptionProvider encryptionProvider;
    
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
        } catch (Exception e) {
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
        } catch (RuntimeException e) {
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
        } catch (Exception e) {
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

    public void killEphemeralObject() {
        List<URI> ephemeralObjects = modelClient.findByType(EphemeralObject.class);
        LOG.info(String.format("EphemeralObjects %s ", ephemeralObjects));
        for (URI id : ephemeralObjects) {
            EphemeralObject ephemeralObject = modelClient.findById(EphemeralObject.class, id);
            URI resourceId = ephemeralObject.getResourceId();
            URI executionStateId = ephemeralObject.getExecutionStateId();

            ExecutionState executionState = modelClient.executionStates().findById(executionStateId);
            String proxyToken = executionState.getProxyToken();
            
            BlockSnapshot snapshot = modelClient.findById(BlockSnapshot.class, resourceId);
            if (! snapshot.getInactive()) {
                ViPRCoreClient client = new ViPRCoreClient(clientConfig);
                proxyUserLogin(client.auth());
                try {
                    client.setProxyToken(proxyToken);
                    client.blockSnapshots().deactivate(resourceId, VolumeDeleteTypeEnum.FULL);
                    modelClient.delete(ephemeralObject);
                } finally {
                    client.auth().logout();
                }
            } else {
                modelClient.delete(ephemeralObject);
            }
        }
    }
    
    private static final String PROXY_USER = "proxyuser";
    private static final String PROXY_USER_PASSWORD_PROPERTY = "system_proxyuser_encpassword"; // NOSONAR ("False positive, field does not
    @Autowired
    private CoordinatorClient coordinatorClient;
    
    private void proxyUserLogin(AuthClient client) {
        String encryptedPassword = coordinatorClient.getPropertyInfo().getProperty(PROXY_USER_PASSWORD_PROPERTY);
        if (StringUtils.isBlank(encryptedPassword)) {
            throw new IllegalArgumentException("Proxy user password is not set");
        }
        String password = encryptionProvider.decrypt(Base64.decodeBase64(encryptedPassword));
        client.getClient().setUsername(PROXY_USER);
        client.getClient().setPassword(password);
        client.login(PROXY_USER, password);
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
