/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.net.URI;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.Task;

/**
 * Interface for resources that support tasks. The type of task/tasks returned are typed to the resource.
 * 
 * @param <T>
 *            the resource type.
 */
public interface TaskResources<T extends DataObjectRestRep> {
    /**
     * Gets the tasks associated with a given resource by ID.
     * <p>
     * API Call: GET <tt><i>baseUrl</i>/{id}/tasks</tt>
     * 
     * @param id
     *            the resource ID.
     * @return the tasks for the resource.
     */
    public Tasks<T> getTasks(URI id);

    /**
     * Gets a single task associated with a given resource by ID.
     * <p>
     * API Call: GET <tt><i>baseUrl</i>/{id}/tasks/{taskId}</tt>
     * 
     * @param id
     *            the resource ID.
     * @param taskId
     *            the task ID.
     * @return the task.
     */
    public Task<T> getTask(URI id, URI taskId);
}
