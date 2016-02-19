/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine.service;

import java.util.Collection;

import com.emc.storageos.db.client.model.uimodels.OrderStatus;

import org.apache.log4j.Logger;

import com.emc.sa.engine.ExecutionTask;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;

public abstract class AbstractExecutionService implements ExecutionService {
    private Logger log;
    private OrderStatus completedOrderStatus = OrderStatus.SUCCESS;
    
	//@Param("externalParam")
	protected String externalParam;
	
	//@Param("extendClass")
	protected String extendClass;	

	//@Param("extendMethods")
	protected String extendMethods;	

	@Override
    public void init() throws Exception {
    }

    @Override
    public void precheck() throws Exception {
    }

    @Override
    public void destroy() {
    }

    protected <T> T execute(ExecutionTask<T> task) {
        return ExecutionUtils.execute(task);
    }

    protected void addAffectedResource(String resourceId) {
        ExecutionUtils.currentContext().getExecutionState().getAffectedResources().add(resourceId);
    }

    protected void addAffectedResources(Collection<String> resourceIds) {
        ExecutionUtils.currentContext().getExecutionState().getAffectedResources().addAll(resourceIds);
    }

    protected void logDebug(String message, Object... args) {
        ExecutionUtils.currentContext().logDebug(message, args);
    }

    protected void logInfo(String message, Object... args) {
        ExecutionUtils.currentContext().logInfo(message, args);
    }

    protected void logWarn(String message, Object... args) {
        ExecutionUtils.currentContext().logWarn(message, args);
    }

    protected void logError(String message, Object... args) {
        ExecutionUtils.currentContext().logError(message, args);
    }

    protected void logError(Throwable cause, String message, Object... args) {
        ExecutionUtils.currentContext().logError(message, args);
    }

    protected void addRollback(ExecutionTask<?> rollback) {
        ExecutionUtils.addRollback(rollback);
    }

    protected void removeRollback(Class<? extends ExecutionTask<?>> type) {
        ExecutionUtils.removeRollback(type);
    }

    protected void clearRollback() {
        ExecutionUtils.clearRollback();
    }

    protected final Logger getLog() {
        if (log == null) {
            log = Logger.getLogger(getClass());
        }
        return log;
    }

    protected void debug(Throwable t, String message, Object... args) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(format(message, args), t);
        }
    }

    protected void info(Throwable t, String message, Object... args) {
        if (getLog().isInfoEnabled()) {
            getLog().info(format(message, args), t);
        }
    }

    protected void warn(Throwable t, String message, Object... args) {
        getLog().warn(format(message, args), t);
    }

    protected void error(Throwable t, String message, Object... args) {
        getLog().error(format(message, args), t);
    }

    protected void debug(String message, Object... args) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(format(message, args));
        }
    }

    protected void info(String message, Object... args) {
        if (getLog().isInfoEnabled()) {
            getLog().info(format(message, args));
        }
    }

    protected void warn(String message, Object... args) {
        getLog().warn(format(message, args));
    }

    protected void error(String message, Object... args) {
        getLog().error(format(message, args));
    }

    protected void debug(Throwable t) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(t, t);
        }
    }

    protected void info(Throwable t) {
        if (getLog().isInfoEnabled()) {
            getLog().info(t, t);
        }
    }

    protected void warn(Throwable t) {
        getLog().warn(t, t);
    }

    protected void error(Throwable t) {
        getLog().error(t, t);
    }

    private String format(String message, Object... args) {
        if (args != null && args.length > 0) {
            return String.format(message, args);
        }
        else {
            return message;
        }
    }

    /**
     * Usually a normal termination of execute will result in a SUCCESSful order. Calling this
     * method will cause the order status to be PARTIAL_SUCCESS instead of SUCCESS.
     */
    protected void setPartialSuccess() {
        completedOrderStatus = OrderStatus.PARTIAL_SUCCESS;
    }

    @Override
    public OrderStatus getCompletedOrderStatus() {
        return completedOrderStatus;
    }

    /** construct an Object array for passing into the fail task methods **/
    protected Object[] args(Object... args) {
        return args;
    }
}
