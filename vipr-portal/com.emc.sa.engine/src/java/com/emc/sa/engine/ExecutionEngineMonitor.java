/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.storageos.db.client.model.uimodels.ExecutionLog.LogLevel;
import com.emc.storageos.db.client.model.uimodels.ExecutionPhase;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.ExecutionStatus;
import com.emc.storageos.db.client.model.uimodels.ExecutionTaskLog;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.zookeeper.SingletonService;
import com.emc.storageos.coordinator.client.service.DistributedDataManager;

/**
 * This class handles both sending the heartbeat for the current engine instance, and monitoring for engine failures.
 * Only a single node will ever be operating as the monitor for engine failures.
 * 
 * Data is stored as /config/sa/engine/{ENGINE_ID}/heartbeat
 * /config/sa/engine/{ENGINE_ID}/orders/{ORDER_ID}.... (replicated for each order)
 * 
 * @author jonnymiller
 */
@Component
public class ExecutionEngineMonitor extends SingletonService {
    private static final long HEART_BEAT = 60000;
    private static final long MAX_AGE = 5 * HEART_BEAT;

    public static final String BASE_PATH = "/config/sa/engine";

    @Autowired
    private ModelClient modelClient;
    private String uniqueId = UUID.randomUUID().toString();
    private DistributedDataManager dataManager;
    @Autowired
    private OrderCleanupHandler drOrderCleanupHandler;
    
    private volatile Thread keepAliveThread;

    @PostConstruct
    public void init() {
        dataManager = getCoordinatorClient().getWorkflowDataManager();

        drOrderCleanupHandler.run();
        
        // Start a keep-alive thread
        keepAliveThread = new Thread(new Runnable() {
            public void run() {
                keepAlive();
            }
        }, "engine-monitor");
        keepAliveThread.setDaemon(true);
        keepAliveThread.start();
        
        log.info("Created SA Engine Monitor with ID " + uniqueId);
    }

    /**
     * Adds the order to the engine state.
     * 
     * @param order
     *            the order.
     */
    public void addOrder(Order order) {
        try {

            dataManager.putData(getOrderPath(uniqueId, order), order.getId());
            if (log.isDebugEnabled()) {
                log.debug("Tracking order: " + order.getId());
            }
        } catch (Exception e) {
            log.error("Error adding order " + order.getId() + " to EngineState", e);
        }
    }

    /**
     * Removes the order from the engine state.
     * 
     * @param order
     *            the order.
     */
    public void removeOrder(Order order) {
        try {
            dataManager.removeNode(getOrderPath(uniqueId, order));
        } catch (Exception e) {
            log.error("Error removing order " + order.getId() + " from EngineState", e);
        }
    }

    /**
     * Performs monitoring of all engines. Only a single node will ever be performing this function at a time.
     */
    @Override
    protected void runService() {
        try {
            while (true) {
                Thread.sleep(HEART_BEAT);

                checkEngineStates();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Checks the state of all registered engines, killing any which are no longer alive.
     */
    private void checkEngineStates() {
        try {
            for (String engine : dataManager.getChildren(BASE_PATH)) {
                Long lastHeartBeat = getHeartBeat(engine);

                if (lastHeartBeat != null) {
                    long age = System.currentTimeMillis() - lastHeartBeat;
                    if (age >= MAX_AGE) {
                        removeEngineState(engine);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error checking Engine States", e);
        }
    }

    /**
     * Stops the engine monitor.
     */
    @Override
    protected void stopService() {
        Thread t = keepAliveThread;
        keepAliveThread = null;
        if (t != null) {
            t.interrupt();
        }
    }

    /**
     * Kills any orders associated with the engine and removes the engine from zookeeper.
     */
    private void removeEngineState(String engineId) {
        if (log.isInfoEnabled()) {
            log.info("Removing engine: " + engineId);
        }

        try {
            if (dataManager.checkExists(getOrdersPath(engineId)) != null) {
                for (String orderId : dataManager.getChildren(getOrdersPath(engineId))) {
                    String message = "Order processing terminated during execution, order was not completed. " +
                            "Check with your administrator. Reboot may have occurred.";
                    killOrder(URI.create(orderId), message);
                    dataManager.removeNode(getOrderPath(engineId, orderId));
                }

                dataManager.removeNode(getEnginePath(engineId));
            }
        } catch (Exception e) {
            log.error("Error whilst removing " + engineId + " engine state", e);
        }
    }

    /**
     * Kills an order that was running within a dead engine.
     * 
     * @param orderId
     *            the order ID.
     * @param detailedMessage
     *            message to be added to order log
     */
    public void killOrder(URI orderId, String detailedMessage) {
        try {
            Order order = modelClient.orders().findById(orderId);
            if (order != null) {
                if (log.isInfoEnabled()) {
                    log.info("Killing order: " + orderId);
                }
                // Mark the order as failed
                order.setOrderStatus(OrderStatus.ERROR.name());
                modelClient.save(order);

                if (order.getExecutionStateId() != null) {
                    ExecutionState execState = modelClient.executionStates().findById(order.getExecutionStateId());
                    // Mark the execution state as failed
                    execState.setExecutionStatus(ExecutionStatus.FAILED.name());
                    modelClient.save(execState);

                    // Find any task logs that are 'in progress' (no elapsed time) and set the elapsed
                    List<ExecutionTaskLog> logs = modelClient.executionTaskLogs().findByIds(execState.getTaskLogIds());
                    for (ExecutionTaskLog log : logs) {
                        if (log.getElapsed() == null) {
                            // Mark any that were in progress as warnings
                            log.setLevel(LogLevel.WARN.name());
                            modelClient.save(log);
                        }
                    }

                    // Add a new log message indicating it failed due to engine termination
                    addTerminationTaskLog(execState, detailedMessage);
                }
            }
        } catch (RuntimeException e) {
            log.error("Failed to terminate order: " + orderId, e);
        }
    }

    /**
     * Adds an execution task log indicating that the engine terminated during execution.
     * 
     * @param state the execution state.
     */
    private void addTerminationTaskLog(ExecutionState state, String detailedMessage) {
        ExecutionTaskLog log = new ExecutionTaskLog();
        log.setDate(new Date());
        log.setLevel(LogLevel.ERROR.toString());
        log.setMessage("Order Terminated");
        log.setDetail(detailedMessage);
        log.setPhase(ExecutionPhase.EXECUTE.name());
        modelClient.save(log);

        state.addExecutionTaskLog(log);
        modelClient.save(state);
    }

    /**
     * Sends a heartbeat every minute, keeping the engine alive.
     */
    private void keepAlive() {
        Thread current = Thread.currentThread();
        try {
            heartBeat();
            while (current == keepAliveThread) {
                Thread.sleep(HEART_BEAT);
                heartBeat();
            }
        } catch (InterruptedException e) {
            log.warn("Heartbeat interrupted", e);
        }
    }

    /**
     * Sends a heartbeat for this engine.
     */
    private void heartBeat() {
        try {
            Long newHeartBeat = System.currentTimeMillis();
            dataManager.putData(getHeartBeatPath(uniqueId), newHeartBeat);
        } catch (Exception e) {
            log.error("Error updating Engine " + uniqueId + " HeartBeat", e);
        }
    }

    private Long getHeartBeat(String engineId) {
        try {
            return (Long) dataManager.getData(getHeartBeatPath(engineId), false);
        } catch (Exception e) {
            log.error("Error getting Engine " + engineId + " Heartbeat", e);
            return null;
        }
    }

    private String getEnginePath(String engineId) {
        return String.format("%s/%s", BASE_PATH, engineId);
    }

    private String getHeartBeatPath(String engineId) {
        return String.format("%s/%s", getEnginePath(engineId), "heartbeat");
    }

    private String getOrdersPath(String engineId) {
        return String.format("%s/%s", getEnginePath(engineId), "orders");
    }

    private String getOrderPath(String engineId, Order order) {
        return getOrderPath(engineId, order.getId().toString());
    }

    private String getOrderPath(String engineId, String orderId) {
        return String.format("%s/%s", getOrdersPath(engineId), orderId);
    }
}
