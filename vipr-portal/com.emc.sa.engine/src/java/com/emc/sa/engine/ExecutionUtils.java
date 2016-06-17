/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.sa.engine.inject.Injector;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.util.Messages;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.ExecutionTaskLog;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderParameter;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.vipr.client.ClientConfig;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Maps;

public class ExecutionUtils {

    private static Messages MESSAGES = new Messages(ExecutionUtils.class, "ViPRService");

    private static final ThreadLocal<ExecutionContext> CONTEXT_HOLDER = new ThreadLocal<ExecutionContext>() {
        protected ExecutionContext initialValue() {
            return new ExecutionContext();
        }
    };

    public static void createContext(ModelClient modelClient, Order order) {
        // Ensure there is no existing context
        destroyContext();

        // Initialize the execution state for this order
        ExecutionState state = modelClient.executionStates().findById(order.getExecutionStateId());
        state.setStartDate(new Date());

        ExecutionContext context = currentContext();
        context.setOrder(order);
        context.setModelClient(modelClient);
        context.setExecutionState(state);

        CatalogService catalogService = modelClient.catalogServices().findById(order.getCatalogServiceId());
        context.setServiceName(catalogService.getLabel());
        List<OrderParameter> orderParameters = modelClient.orderParameters().findByOrderId(order.getId());
        Map<String, Object> params = Maps.newLinkedHashMap();
        for (OrderParameter param : orderParameters) {
            params.put(param.getLabel(), param.getValue());
        }
        context.setParameters(params);
    }

    public static void destroyContext() {
        CONTEXT_HOLDER.remove();
    }

    public static ExecutionContext currentContext() {
        return CONTEXT_HOLDER.get();
    }

    public static <T> T execute(ExecutionTask<T> task) throws ExecutionException {
        return execute(task, currentContext());
    }

    protected static <T> T execute(ExecutionTask<T> task, ExecutionContext context) throws ExecutionException {
        ExecutionTaskLog log = context.logCurrentTask(task);
        // Executing if order not in a paused state. If paused, poll while waiting for order to go into executing state
        String orderStatus = context.getModelClient().orders().findById(context.getOrder().getId()).getOrderStatus();
        long startTime = System.currentTimeMillis();
        try {
            while (OrderStatus.PAUSED.name().equalsIgnoreCase(orderStatus)) {
                Thread.sleep(1000);
                // requery order to get its updated status
                orderStatus = context.getModelClient().orders().findById(context.getOrder().getId()).getOrderStatus();
            }

            injectValues(task, context);
            T result = task.executeTask();
            long elapsedTime = System.currentTimeMillis() - startTime;

            context.updateCurrentTask(log, task, elapsedTime);
            return result;
        } catch (Exception e) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            context.updateCurrentTask(log, task, elapsedTime, e);
            throw new ExecutionException(e);
        }
    }

    public static <T> ViPRTaskMonitor<T> startViprTask(ExecutionTask<Task<T>> task) throws ExecutionException {
        return startViprTask(task, currentContext());
    }

    protected static <T> ViPRTaskMonitor<T> startViprTask(ExecutionTask<Task<T>> task, ExecutionContext context)
            throws ExecutionException {
        ExecutionTaskLog log = context.logCurrentTask(task);

        long startTime = System.currentTimeMillis();
        try {
            injectValues(task, context);
            Task<T> result = task.executeTask();
            return new ViPRTaskMonitor<T>(context, log, result);
        } catch (Exception e) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            context.updateCurrentTask(log, task, elapsedTime, e);
            throw new ExecutionException(e);
        }
    }

    public static <T> ViPRTasksMonitor<T> startViprTasks(ExecutionTask<Tasks<T>> task) throws ExecutionException {
        return startViprTasks(task, currentContext());
    }

    protected static <T> ViPRTasksMonitor<T> startViprTasks(ExecutionTask<Tasks<T>> task, ExecutionContext context)
            throws ExecutionException {
        ExecutionTaskLog log = context.logCurrentTask(task);

        long startTime = System.currentTimeMillis();
        try {
            injectValues(task, context);
            Tasks<T> result = task.executeTask();
            return new ViPRTasksMonitor<T>(context, log, result);
        } catch (Exception e) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            context.updateCurrentTask(log, task, elapsedTime, e);
            throw new ExecutionException(e);
        }
    }

    /**
     * Injects any values into the execution task.
     * 
     * @param task
     *            the execution task.
     * @param context
     *            the execution context.
     */
    public static <T> void injectValues(ExecutionTask<T> task, ExecutionContext context) {
        Injector.inject(task, context.getInjectedValues());
    }

    public static void addRollback(ExecutionTask<?> rollbackTask) {
        currentContext().getRollback().add(rollbackTask);
    }

    /**
     * Removes a rollback task of a certain type.
     * 
     * @param type
     *            the type of the rollback task.
     */
    public static void removeRollback(Class<? extends ExecutionTask<?>> type) {
        Iterator<ExecutionTask<?>> iter = currentContext().getRollback().iterator();
        while (iter.hasNext()) {
            ExecutionTask<?> task = iter.next();
            if (type.equals(task.getClass())) {
                iter.remove();
            }
        }
    }

    /**
     * Clears all rollback tasks.
     */
    public static void clearRollback() {
        currentContext().getRollback().clear();
    }

    public static void addAffectedResource(String resourceId) {
        currentContext().getExecutionState().addAffectedResource(resourceId);
    }

    public static boolean acquireLock(String name) {
        return currentContext().getLockManager().acquireLock(name);
    }

    public static void releaseLock(String name) {
        currentContext().getLockManager().releaseLock(name);
    }

    public static void fail(String taskNameKey, Object[] detailArgs, Object... messageArgs) {
        execute(new FailTask(taskNameKey, detailArgs, messageArgs));
    }

    public static void fail(String taskNameKey, Object detailArg, Object... messageArgs) {
        fail(taskNameKey, new Object[] { detailArg }, messageArgs);
    }

    public static void fail(String taskNameKey, Exception exception, Object[] detailArgs, Object... messageArgs) {
        execute(new FailTask(taskNameKey, exception, detailArgs, messageArgs));
    }

    public static void fail(String taskNameKey, Exception exception, Object detailArg, Object... messageArgs) {
        fail(taskNameKey, exception, new Object[] { detailArg }, messageArgs);
    }

    public static String getMessage(String key, Object... args) {
        try {
            String message = MESSAGES.get(key, args);
            if (StringUtils.isNotBlank(message)) {
                return message;
            }
        } catch (MissingResourceException e) {
            // fall out and return the original key
        }

        return key;
    }

    /**
     * Waits for a list of tasks to complete, handling each as they complete. This uses the default client task polling
     * interval.
     * 
     * 
     * @param tasks
     *            the tasks to monitor.
     * @param handler
     *            the task handler.
     * @return true if all tasks completed successfully.
     */
    public static <T> boolean waitForTask(List<ViPRTaskMonitor<T>> tasks, ViPRTaskHandler<T> handler) {
        return waitForTask(tasks, handler, ClientConfig.DEFAULT_TASK_POLLING_INTERVAL);
    }

    /**
     * Waits for a list of tasks to complete, handling each as they complete.
     * 
     * @param tasks
     *            the tasks to monitor.
     * @param handler
     *            the task handler.
     * @param delay
     *            the delay between each round of task polling.
     * @return true if all tasks completed successfully.
     */
    public static <T> boolean waitForTask(List<ViPRTaskMonitor<T>> tasks, ViPRTaskHandler<T> handler, long delay) {
        try {
            boolean hasErrors = false;
            List<ViPRTaskMonitor<T>> remaining = new ArrayList<>(tasks);
            while (!remaining.isEmpty()) {
                // Recheck each task for completion
                List<ViPRTaskMonitor<T>> completed = new ArrayList<>();
                for (ViPRTaskMonitor<T> task : remaining) {
                    if (task.check()) {
                        completed.add(task);
                    }
                }

                // Remove all completed tasks from the remaining and handle them
                remaining.removeAll(completed);
                for (ViPRTaskMonitor<T> task : completed) {
                    if (!completeTask(task, handler)) {
                        hasErrors = true;
                    }
                }

                // Sleep for a short time if there are remaining tasks
                if (!remaining.isEmpty()) {
                    Thread.sleep(delay);
                }
            }
            return hasErrors;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutionException(e);
        }
    }

    /**
     * Completes a task using the given handler.
     * 
     * @param task
     *            the task that was completed.
     * @param handler
     *            the task handler.
     * @return true if the task completed successfully.
     */
    private static <T> boolean completeTask(ViPRTaskMonitor<T> task, ViPRTaskHandler<T> handler) {
        try {
            T value = task.getValue();
            handler.onSuccess(task.getTask(), value);
            return true;
        } catch (ExecutionException e) {
            handler.onFailure(task.getTask(), e);
            return false;
        }
    }

    /**
     * Checks for any errors in the tasks and throws the first found error.
     * 
     * @param tasks
     *            the tasks to check.
     */
    public static <T> void checkForError(List<ViPRTaskMonitor<T>> tasks) {
        for (ViPRTaskMonitor<T> task : tasks) {
            if (task.getError() != null) {
                throw task.getError();
            }
        }
    }

    /**
     * Interface used to provide common task handling to a group of tasks.
     * 
     * @param <T>
     *            the return type of the task.
     */
    public static interface ViPRTaskHandler<T> {
        public void onSuccess(Task<T> task, T value);

        public void onFailure(Task<T> task, ExecutionException e);
    }
}
