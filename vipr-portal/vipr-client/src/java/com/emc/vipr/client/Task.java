/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.vipr.client.exceptions.TimeoutException;
import com.emc.vipr.client.exceptions.ViPRException;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.client.core.impl.TaskUtil;
import java.net.URI;
import java.util.Calendar;
import java.util.List;

/**
 * Representation of an asynchronous task returned from an operation.
 * 
 * @param <R> Type of the underlying resource running the operation.
 */
public class Task<R> {
    private RestClient client;
    private TaskResourceRep task;
    private Class<? extends R> resourceClass;

    public Task(RestClient client, TaskResourceRep task, Class<? extends R> resourceClass) {
        this.client = client;
        this.task = task;
        this.resourceClass = resourceClass;
    }

    /**
     * Gets the underlying task resource.
     * 
     * @return TaskResourceRep for this task.
     */
    public TaskResourceRep getTaskResource() {
        return task;
    }

    public URI getResourceId() {
        NamedRelatedResourceRep resource = getResource();
        if (resource == null) {
            return null;
        }
        return resource.getId();
    }

    public boolean isComplete() {
        return TaskUtil.isComplete(task);
    }

    public boolean isError() {
        return TaskUtil.isError(task);
    }

    /**
     * Delegates to the underlying task.
     * 
     * @return Reference to the task resource.
     */
    public NamedRelatedResourceRep getResource() {
        return task.getResource();
    }

    /**
     * Delegates to the underlying task.
     */
    public List<NamedRelatedResourceRep> getAssociatedResources() {
        return task.getAssociatedResources();
    }

    /**
     * Delegates to the underlying task.
     */
    public String getDescription() {
        return task.getDescription();
    }

    /**
     * Delegates to the underlying task.
     */
    public Calendar getEndTime() {
        return task.getEndTime();
    }

    /**
     * Delegates to the underlying task.
     */
    public String getMessage() {
        return task.getMessage();
    }

    /**
     * Delegates to the underlying task.
     */
    public String getOpId() {
        return task.getOpId();
    }

    /**
     * Delegates to the underlying task.
     */
    public Calendar getStartTime() {
        return task.getStartTime();
    }

    /**
     * Queries the task and updates information on this task.
     * 
     * @return This task.
     */
    public Task<R> refresh() {
        task = TaskUtil.refresh(client, task);
        return this;
    }

    /**
     * Waits for a task to complete (go into a pending or error state). If an error occurs
     * it will be thrown as an exception.
     * 
     * @throws ViPRException Thrown if the task is in an error state.
     * @return This task.
     */
    public Task<R> waitFor() throws ViPRException {
        return waitFor(-1);
    }

    /**
     * Waits for a task to complete (go into a pending or error state). If an error occurs
     * it will be thrown as an exception.
     * 
     * @param timeoutMillis Timeout after a number of milliseconds
     * @throws TimeoutException Thrown if a timeout occurs.
     * @throws ViPRException Thrown if the task is in an error state.
     * @return This task.
     */
    public Task<R> waitFor(long timeoutMillis) throws ViPRException {
        doTaskWait(timeoutMillis);
        TaskUtil.checkForError(task);
        return this;
    }

    void doTaskWait(long timeoutMillis) throws TimeoutException {
        task = TaskUtil.waitForTask(client, task, timeoutMillis);
    }

    /**
     * Waits for a task to complete (go into a pending or error state). If an error occurs
     * it will be thrown as an exception. If the task was successful this returns the
     * actual object for the underlying resource.
     * 
     * @throws TimeoutException Thrown if a timeout occurs.
     * @throws ViPRException Thrown if the task is in an error state.
     * @return Resource object.
     */
    public R get() {
        waitFor();
        return doGetResource();
    }

    /**
     * Waits for a task to complete (go into a pending or error state). If an error occurs
     * it will be thrown as an exception. If the task was successful this returns the
     * actual object for the underlying resource.
     * 
     * @param timeoutMillis Timeout after a number of milliseconds
     * @throws TimeoutException Thrown if a timeout occurs.
     * @throws ViPRException Thrown if the task is in an error state.
     * @return Resource object.
     */
    public R get(long timeoutMillis) {
        waitFor(timeoutMillis);
        return doGetResource();
    }

    R doGetResource() {
        return client.get(resourceClass, task.getResource().getLink().getLinkRef().toString());
    }
}
