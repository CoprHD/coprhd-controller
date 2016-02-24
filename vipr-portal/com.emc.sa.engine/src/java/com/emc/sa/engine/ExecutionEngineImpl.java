/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine;

import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.engine.lock.ExecutionLockManager;
import com.emc.sa.engine.service.ExecutionService;
import com.emc.sa.engine.service.ExecutionServiceFactory;
import com.emc.sa.engine.service.ServiceNotFoundException;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.ExecutionStatus;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.google.common.collect.Lists;

@Component
public class ExecutionEngineImpl implements ExecutionEngine {
    private static final Logger LOG = Logger.getLogger(ExecutionEngineImpl.class);

    @Autowired
    private ModelClient modelClient;
    @Autowired
    private ExecutionServiceFactory serviceFactory;
    @Autowired
    private CoordinatorClient coordinatorClient;

    public ModelClient getModelClient() {
        return modelClient;
    }

    public void setModelClient(ModelClient modelClient) {
        this.modelClient = modelClient;
    }

    public ExecutionServiceFactory getServiceFactory() {
        return serviceFactory;
    }

    public void setServiceFactory(ExecutionServiceFactory serviceFactory) {
        this.serviceFactory = serviceFactory;
    }

    public CoordinatorClient getCoordinatorClient() {
        return coordinatorClient;
    }

    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    @Override
    public void executeOrder(Order order) {
        LOG.info(String.format("Executing order: %s [%s]", order.getOrderNumber(), order.getId()));
        initContext(order);
        try {
            updateOrderStatus(order, OrderStatus.EXECUTING);
            ExecutionService service = createService(order);
            runService(service);
            orderCompleted(order, service.getCompletedOrderStatus());
        } catch (ExecutionException e) {
            orderFailed(order, e);
        } catch (RuntimeException e) {
            LOG.error("Unexpected error executing order: " + order.getId());
            orderFailed(order, new ExecutionException(e));
        } finally {
            destroyContext(order);
        }
    }

    protected void initContext(Order order) {
        ExecutionUtils.createContext(getModelClient(), order);
        // Adds execution lock support
        ExecutionLockManager lockManager = new ExecutionLockManager(getCoordinatorClient());
        ExecutionUtils.currentContext().setLockManager(lockManager);
    }

    protected void destroyContext(Order order) {
        ExecutionLockManager lockManager = ExecutionUtils.currentContext().getLockManager();
        if (lockManager != null) {
            lockManager.destroyLocks();
        }
        ExecutionUtils.destroyContext();
    }

    protected void orderCompleted(Order order, OrderStatus status) {
        LOG.info("Order complete: " + order.getId());
        order.setDateCompleted(new Date());
        updateOrderStatus(order, status);
        finishExecuting(ExecutionStatus.COMPLETED);
    }

    protected void orderFailed(Order order, ExecutionException e) {
        LOG.error("Order failed: " + order.getId(), e.getCause());
        order.setMessage(getErrorMessage(e));
        order.setDateCompleted(new Date());
        updateOrderStatus(order, OrderStatus.ERROR);
        finishExecuting(ExecutionStatus.FAILED);
    }

    protected String getErrorMessage(ExecutionException e) {
        Throwable rootCause = ExceptionUtils.getRootCause(e);
        if (StringUtils.isNotBlank(rootCause.getMessage())) {
            return rootCause.getMessage();
        }
        else {
            return ExceptionUtils.getFullStackTrace(e.getCause());
        }
    }

    protected void runService(ExecutionService service) {
        try {
            init(service);
            precheck(service);
            preLaunch(service);
            execute(service);
            postLaunch(service);
            postcheck(service);
        } catch (ExecutionException e) {
            logError(e, service);
            try {
                rollback();
            } catch (ExecutionException re) {
                logError(re, service);
            }
            throw e;
        } finally {
            destroy(service);
        }
    }



	protected ExecutionService createService(Order order) {
        try {
            CatalogService catalogService = getModelClient().catalogServices().findById(order.getCatalogServiceId());

            return serviceFactory.createService(order, catalogService);
        } catch (ServiceNotFoundException e) {
            LOG.error("Could not create service for order: " + order.getId(), e);
            throw new ExecutionException(e);
        } catch (RuntimeException e) {
            LOG.error("Unexpected exception while creating service for order: " + order.getId(), e);
            throw new ExecutionException(e);
        }
    }

    protected void init(ExecutionService service) throws ExecutionException {
        try {
            ExecutionContext context = ExecutionUtils.currentContext();
            LOG.debug("Initialize " + context.getServiceName());

            bindParameters(context, service);
            service.init();
        } catch (ExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
    }

    protected void precheck(ExecutionService service) throws ExecutionException {
        try {
            ExecutionContext context = ExecutionUtils.currentContext();
            LOG.debug("Precheck " + context.getServiceName());

            updateExecutionStatus(ExecutionStatus.PRECHECK);
            service.precheck();
        } catch (ExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
    }

    private void preLaunch(ExecutionService service) {
        try {
            ExecutionContext context = ExecutionUtils.currentContext();
            LOG.debug("preLaunch " + context.getServiceName() +context.getParameters().get("externalParam"));

            updateExecutionStatus(ExecutionStatus.PRELAUNCH);
            service.preLaunch();
        } catch (ExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
		
	}
    
    protected void execute(ExecutionService service) throws ExecutionException {
        try {
            ExecutionContext context = ExecutionUtils.currentContext();
            LOG.debug("Executing " + context.getServiceName());

            updateExecutionStatus(ExecutionStatus.EXECUTE);
            service.execute();
        } catch (ExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
    }
    
    private void postLaunch(ExecutionService service) {
        try {
            ExecutionContext context = ExecutionUtils.currentContext();
            LOG.debug("postLaunch " + context.getServiceName());
            updateExecutionStatus(ExecutionStatus.POSTLAUNCH);
            service.postLaunch();
        } catch (ExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
		
	}
    
    private void postcheck(ExecutionService service) {
        try {
            ExecutionContext context = ExecutionUtils.currentContext();
            LOG.debug("postCheck " + context.getServiceName());
            updateExecutionStatus(ExecutionStatus.POSTLAUNCH);
            service.postcheck();
        } catch (ExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
		
	}

    protected void destroy(ExecutionService service) {
        try {
            ExecutionContext context = ExecutionUtils.currentContext();
            LOG.debug("Destroy " + context.getServiceName());

            service.destroy();
        } catch (ExecutionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ExecutionException(e);
        }
    }

    protected void rollback() throws RollbackException {
        ExecutionContext context = ExecutionUtils.currentContext();
        if (context.getRollback().size() > 0) {
            LOG.debug("Rolling back: " + context.getServiceName());
            try {
                updateExecutionStatus(ExecutionStatus.ROLLBACK);

                // Execute the rollbacks in the opposite order
                List<ExecutionTask<?>> rollback = Lists.reverse(context.getRollback());
                for (ExecutionTask<?> task : rollback) {
                    ExecutionUtils.execute(task);
                }
            } catch (ExecutionException e) {
                throw new RollbackException(e.getCause());
            } catch (Exception e) {
                throw new RollbackException(e);
            }
        }
    }

    protected void logError(ExecutionException error, ExecutionService service) {
        try {
            String message = getDeepestCauseMessage(error);
            if (message == null) {
                message = error.getCause().getClass().getName();
            }

            ExecutionContext context = ExecutionUtils.currentContext();
            context.logError(error.getCause(), message);
        } catch (RuntimeException e) {
            LOG.error("Unexpected runtime exception while logging error", e);
        }
    }

    protected String getDeepestCauseMessage(Throwable t) {
        @SuppressWarnings("unchecked")
        List<Throwable> throwables = ExceptionUtils.getThrowableList(t);
        String message = null;
        for (Throwable cause : throwables) {
            if (cause.getMessage() != null) {
                message = cause.getMessage();
            }
        }
        return message;
    }

    protected void bindParameters(ExecutionContext context, ExecutionService service) {
        BindingUtils.bind(service, context.getParameters());
    }

    protected void updateOrderStatus(Order order, OrderStatus status) {
        order.setOrderStatus(status.name());
        if (modelClient != null) {
            modelClient.save(order);
        }
    }

    protected void updateExecutionStatus(ExecutionStatus status) {
        ExecutionState state = ExecutionUtils.currentContext().getExecutionState();
        state.setExecutionStatus(status.name());
        if (modelClient != null) {
            modelClient.save(state);
        }
    }

    protected void finishExecuting(ExecutionStatus status) {
        ExecutionState state = ExecutionUtils.currentContext().getExecutionState();
        state.setCurrentTask("");
        state.setEndDate(new Date());
        state.setExecutionStatus(status.name());
        if (modelClient != null) {
            modelClient.save(state);
        }
    }
}
