/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.math.NumberUtils;

import com.emc.sa.engine.lock.ExecutionLockManager;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.model.uimodels.ExecutionLog;
import com.emc.storageos.db.client.model.uimodels.ExecutionLog.LogLevel;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.db.client.model.uimodels.ExecutionPhase;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.ExecutionStatus;
import com.emc.storageos.db.client.model.uimodels.ExecutionTaskLog;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.ScheduledEvent;
import com.emc.sa.model.dao.ModelClient;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class ExecutionContext {

    /** Provides access to execution locks. */
    private ExecutionLockManager lockManager;

    /** Provides access to data objects. */
    private ModelClient modelClient;

    /** The state of the current execution. */
    private ExecutionState executionState;

    /** The name of the service being executed. */
    private String serviceName;

    /** The parameters available to bind to a service. */
    private Map<String, Object> parameters;

    /** The injected values available to execution tasks. */
    private Map<Class<?>, Object> injectedValues;

    /** The current rollback list to invoke if an error occurs. */
    private List<ExecutionTask<?>> rollback;
    /** The order id that created the execution context. */
    private Order order;
    
    /** Associated scheduled event if it is recurring order  */
    private ScheduledEvent scheduledEvent;

    private CoordinatorClient coordinatorClient;
    
    public ExecutionLockManager getLockManager() {
        return lockManager;
    }

    public void setLockManager(ExecutionLockManager lockManager) {
        this.lockManager = lockManager;
    }

    public ModelClient getModelClient() {
        return modelClient;
    }

    public void setModelClient(ModelClient modelClient) {
        this.modelClient = modelClient;
    }

    public ExecutionState getExecutionState() {
        return executionState;
    }

    public void setExecutionState(ExecutionState executionState) {
        this.executionState = executionState;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Map<String, Object> getParameters() {
        if (parameters == null) {
            parameters = Maps.newHashMap();
        }
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Map<Class<?>, Object> getInjectedValues() {
        if (injectedValues == null) {
            injectedValues = Maps.newHashMap();
        }
        return injectedValues;
    }

    public <T> void addInjectedValue(Class<? extends T> clazz, T value) {
        getInjectedValues().put(clazz, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getInjectedValue(Class<? extends T> clazz) {
        return (T) getInjectedValues().get(clazz);
    }

    public List<ExecutionTask<?>> getRollback() {
        if (rollback == null) {
            rollback = Lists.newArrayList();
        }
        return rollback;
    }

    public void setRollback(List<ExecutionTask<?>> rollback) {
        this.rollback = rollback;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public ExecutionStatus getExecutionStatus() {
        return ExecutionStatus.valueOf(getExecutionState().getExecutionStatus());
    }

    public void setExecutionStatus(ExecutionStatus status) {
        executionState.setExecutionStatus(status.name());
    }

    public void setCurrentTask(ExecutionTask<?> task) {
        executionState.setCurrentTask(task != null ? task.getName() : null);
    }

    public ScheduledEvent getScheduledEvent() {
		return scheduledEvent;
	}

	public void setScheduledEvent(ScheduledEvent scheduledEvent) {
		this.scheduledEvent = scheduledEvent;
	}
	
	public CoordinatorClient getCoordinatorClient() {
        return coordinatorClient;
    }

    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    protected ExecutionPhase getExecutionPhase() {
        switch (getExecutionStatus()) {
            case PRECHECK:
                return ExecutionPhase.PRECHECK;
            case EXECUTE:
                return ExecutionPhase.EXECUTE;
            case ROLLBACK:
                return ExecutionPhase.ROLLBACK;
            default:
                return ExecutionPhase.NONE;
        }
    }

    protected String getExecutionPhaseName() {
        ExecutionPhase phase = getExecutionPhase();
        return phase != null ? phase.name() : null;
    }

    public void logMessage(LogLevel level, Throwable t, String key, Object... args) {
        ExecutionLog log = new ExecutionLog();
        log.setDate(new Date());
        log.setLevel(level.name());
        if (args.length > 0) {
            log.setMessage(ExecutionUtils.getMessage(key, args));
        }
        else {
            log.setMessage(ExecutionUtils.getMessage(key));
        }
        if (t != null) {
            log.addStackTrace(t);
        }
        log.setPhase(getExecutionPhaseName());
        modelClient.save(log);

        executionState.addExecutionLog(log);
        modelClient.save(executionState);
    }

    public void logDebug(String key, Object... args) {
        logMessage(LogLevel.DEBUG, null, key, args);
    }

    public void logInfo(String key, Object... args) {
        logMessage(LogLevel.INFO, null, key, args);
    }

    public void logWarn(String key, Object... args) {
        logMessage(LogLevel.WARN, null, key, args);
    }

    public void logError(String key, Object... args) {
        logMessage(LogLevel.ERROR, null, key, args);
    }

    public void logError(Throwable t, String key, Object... args) {
        logMessage(LogLevel.ERROR, t, key, args);
    }

    public ExecutionTaskLog logCurrentTask(ExecutionTask<?> task) {
        setCurrentTask(task);
        ExecutionTaskLog log = new ExecutionTaskLog();
        log.setDate(new Date());
        log.setLevel(LogLevel.INFO.name());
        log.setMessage(task.getName());
        log.setDetail(task.getDetail());
        log.setPhase(getExecutionPhaseName());
        modelClient.save(log);

        executionState.addExecutionTaskLog(log);
        modelClient.save(executionState);
        return log;
    }

    public void updateCurrentTask(ExecutionTaskLog log, ExecutionTask<?> task, long elapsedTime) {
        log.setLevel(LogLevel.INFO.name());
        log.setMessage(task.getName());
        log.setDetail(task.getDetail());
        log.setElapsed(elapsedTime);
        modelClient.save(log);
    }

    public void updateCurrentTask(ExecutionTaskLog log, ExecutionTask<?> task, long elapsedTime, Throwable error) {
        log.setLevel(LogLevel.ERROR.name());
        log.addStackTrace(error);
        log.setMessage(task.getName());
        log.setDetail(task.getDetail());
        log.setElapsed(elapsedTime);
        modelClient.save(log);
    }

    public void updateTaskLog(ExecutionTaskLog log, long elapsedTime) {
        log.setLevel(LogLevel.INFO.name());
        log.setElapsed(elapsedTime);
        modelClient.save(log);
    }

    public void updateTaskLog(ExecutionTaskLog log, long elapsedTime, Throwable error) {
        log.setLevel(LogLevel.ERROR.name());
        log.setElapsed(elapsedTime);
        log.addStackTrace(error);
        modelClient.save(log);
    }
    
    public long getResourceLimit(String name) {
        PropertyInfo sysprops = coordinatorClient.getPropertyInfo();
        String value = sysprops.getProperty(name);
        return NumberUtils.toLong(value);
    }
}
