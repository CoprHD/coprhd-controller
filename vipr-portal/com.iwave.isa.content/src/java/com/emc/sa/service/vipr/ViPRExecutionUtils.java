/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.ExecutionTask;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.core.util.ResourceUtils;

/**
 * Moved many ViPR-specific utility methods for execution into one class for ease of use.
 * 
 * @author jonnymiller
 */
public class ViPRExecutionUtils {
    public static URI uri(String id) {
        return ResourceUtils.uri(id);
    }

    public static List<URI> uris(List<String> ids) {
        return ResourceUtils.uris(ids);
    }

    /**
     * Gets the tenant for the currently executing order.
     * 
     * @return the tenant for the current order.
     */
    public static URI getOrderTenant() {
        return uri(ExecutionUtils.currentContext().getOrder().getTenant());
    }

    public static <T> T execute(ExecutionTask<T> task) {
        return ExecutionUtils.execute(task);
    }

    public static <T> void addInjectedValue(Class<? extends T> clazz, T value) {
        ExecutionUtils.currentContext().addInjectedValue(clazz, value);
    }

    public static void addAffectedResources(Iterable<? extends DataObjectRestRep> values) {
        if (values != null) {
            for (DataObjectRestRep value : values) {
                addAffectedResource(value);
            }
        }
    }

    public static void addAffectedResource(URI value) {
        if (value != null) {
            addAffectedResource(value.toString());
        }
    }

    public static void addAffectedResource(DataObjectRestRep value) {
        if (value != null) {
            addAffectedResource(value.getId());
        }
    }

    public static void addAffectedResource(Task<? extends DataObjectRestRep> task) {
        if (task != null) {
            addAffectedResource(task.getResourceId());
            for (URI id : ResourceUtils.refIds(task.getAssociatedResources())) {
                addAffectedResource(id);
            }
        }
    }

    public static void addAffectedResources(Tasks<? extends DataObjectRestRep> tasks) {
        if (tasks != null) {
            for (Task<? extends com.emc.storageos.model.DataObjectRestRep> task : tasks.getTasks()) {
                addAffectedResource(task);
            }
        }
    }

    public static void addAffectedResource(String resourceId) {
        ExecutionUtils.addAffectedResource(resourceId);
    }

    public static void addRollback(ExecutionTask<?> rollbackTask) {
        ExecutionUtils.addRollback(rollbackTask);
    }

    public static void logDebug(String message, Object... args) {
        ExecutionUtils.currentContext().logDebug(message, args);
    }

    public static void logInfo(String message, Object... args) {
        ExecutionUtils.currentContext().logInfo(message, args);
    }

    public static void logWarn(String message, Object... args) {
        ExecutionUtils.currentContext().logWarn(message, args);
    }

    public static void logError(String message, Object... args) {
        ExecutionUtils.currentContext().logError(message, args);
    }

    public static void logError(Throwable t, String message, Object... args) {
        ExecutionUtils.currentContext().logError(t, message, args);
    }
}
