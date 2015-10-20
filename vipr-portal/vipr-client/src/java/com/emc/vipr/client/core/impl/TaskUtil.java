/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.impl;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import com.emc.vipr.client.AuthClient;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import com.emc.vipr.client.exceptions.ServiceErrorsException;
import com.emc.vipr.client.exceptions.TimeoutException;
import com.emc.vipr.client.exceptions.ViPRException;
import com.emc.vipr.client.impl.RestClient;

public class TaskUtil {

    public enum State {
        queued, pending, error
    }

    public static TaskResourceRep refresh(RestClient client, TaskResourceRep task) {
        RestLinkRep link = task.getLink();
        if (link == null) {
            throw new ViPRException("Task has no link");
        }
        return client.get(TaskResourceRep.class, link.getLinkRef().toString());
    }

    public static TaskResourceRep waitForTask(RestClient client, TaskResourceRep task, long timeoutMillis) {
        long startTime = System.currentTimeMillis();
        while (isRunning(task)) {
            if (timeoutMillis > 0 && (System.currentTimeMillis() - startTime) > timeoutMillis) {
                throw new TimeoutException("Timed out waiting for task to complete");
            }
            try {
                Thread.sleep(client.getConfig().getTaskPollingInterval());
            } catch (InterruptedException e) {
                throw new ViPRException(e);
            }
            refreshSession(client);
            task = refresh(client, task);
        }
        return task;
    }

    public static List<TaskResourceRep> waitForTasks(RestClient client, List<TaskResourceRep> tasks, long timeoutMillis) {
        List<TaskResourceRep> newTasks = new ArrayList<TaskResourceRep>();
        for (TaskResourceRep task : tasks) {
            newTasks.add(waitForTask(client, task, timeoutMillis));
        }
        return newTasks;
    }

    public static boolean isRunning(TaskResourceRep task) {
        return State.pending.name().equalsIgnoreCase(task.getState()) ||
                State.queued.name().equalsIgnoreCase(task.getState());
    }

    public static boolean isQueued(TaskResourceRep task) {
        return State.queued.name().equalsIgnoreCase(task.getState());
    }

    public static boolean isComplete(TaskResourceRep task) {
        return !isRunning(task);
    }

    public static boolean isError(TaskResourceRep task) {
        return task == null || task.getState() == null || State.error.name().equalsIgnoreCase(task.getState());
    }

    /**
     * Checks a task state to see if it is in error. If it is, throws an
     * exception.
     * 
     * @param task Task to check for errors on
     */
    public static void checkForError(TaskResourceRep task) {
        if (isError(task)) {
            throw new ServiceErrorException(taskToError(task));
        }
    }

    public static void checkForErrors(List<TaskResourceRep> tasks) {
        List<ServiceErrorRestRep> errors = new ArrayList<ServiceErrorRestRep>();
        for (TaskResourceRep task : tasks) {
            if (task != null && isError(task)) {
                errors.add(taskToError(task));
            }
        }
        if (errors.size() == 1) {
            throw new ServiceErrorException(errors.get(0));
        }
        else if (errors.size() > 1) {
            throw new ServiceErrorsException(errors);
        }
    }

    private static ServiceErrorRestRep taskToError(TaskResourceRep task) {
        ServiceErrorRestRep serviceError = task.getServiceError();
        if (task.getState() == null) {
            serviceError = new ServiceErrorRestRep();
            serviceError.setCodeDescription("Task state is null. Unable to determine success of task");
            serviceError.setDetailedMessage("");
        }
        else if (serviceError == null) {
            serviceError = new ServiceErrorRestRep();
            serviceError.setCodeDescription(task.getMessage() == null ? "No Message" : task.getMessage());
            serviceError.setDetailedMessage("");
        }
        return serviceError;
    }

    private synchronized static void refreshSession(RestClient client) {
        if (client.getLoginTime() > 0
                && (System.currentTimeMillis() - client.getLoginTime()) > client.getConfig().getSessionKeyRenewTimeout()
                && client.getUsername() != null && client.getPassword() != null) {
            AuthClient authClient = new AuthClient(client);
            authClient.logout();
            authClient.login(client.getUsername(), client.getPassword());
            client.setProxyToken(authClient.proxyToken());
        }
    }
}
