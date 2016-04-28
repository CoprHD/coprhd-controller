/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.api.mapper.TaskMapper.toTaskList;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.impl.resource.utils.AsyncTaskExecutorService;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.security.authorization.InheritCheckPermission;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Base class for all resources with
 * 1. support for /<base path>/{id}/...
 * 2. support for tagging
 */
public abstract class TaskResourceService extends TaggedResource {
    private static Logger _log = LoggerFactory.getLogger(TaggedResource.class);

    @Autowired
    protected AsyncTaskExecutorService _asyncTaskService;

    /**
     * Get all recent tasks for a specific resource
     * 
     * @prereq none
     * @param id the URN of a ViPR object to query
     * @brief List tasks for resource
     * @return All tasks for the object
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/tasks")
    @InheritCheckPermission
    public TaskList getTasks(@PathParam("id") URI id) {
        List<Task> tasks = TaskUtils.findResourceTasks(_dbClient, id);

        return toTaskList(tasks);
    }

    /**
     * Get a specific task for a specific object
     * 
     * This method is deprecated, use /vdc/tasks/{id} instead
     * 
     * @prereq none
     * @param id the URN of a ViPR object to query
     * @param requestId Identifier for the task operation of the object
     * @brief Show task
     * @return task representation
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/tasks/{op_id}/")
    @InheritCheckPermission
    @Deprecated
    public TaskResourceRep getTask(@PathParam("id") URI id,
            @PathParam("op_id") URI requestId) {

        Task task = null;
        if (URIUtil.isValid(requestId)) {
            task = _dbClient.queryObject(Task.class, requestId);
        } else {
            task = TaskUtils.findTaskForRequestId(_dbClient, id, requestId.toString());
        }

        if (task == null) {
            throw APIException.badRequests.invalidParameterNoOperationForTaskId(requestId);
        }

        return toTask(task);
    }

    /**
     * Given a list of Tenants and DataObject references, check if any of the DataObjects have pending
     * Tasks against them. If so, generate an error that this cannot be deleted.
     * 
     * @param tenants - [in] List of Tenant URIs
     * @param dataObjects - [in] List of DataObjects to check
     */
    protected void checkForPendingTasks(Collection<URI> tenants, Collection<? extends DataObject> dataObjects) {
        for (URI tenant : tenants) {
            checkForPendingTasks(tenant, dataObjects);
        }
    }

    /**
     * Given a Tenant and DataObject references, check if any of the DataObjects have pending
     * Tasks against them. If so, generate an error that this cannot be deleted.
     * 
     * @param tenant - [in] Tenant URI
     * @param dataObjects - [in] List of DataObjects to check
     */
    private void checkForPendingTasks(URI tenant, Collection<? extends DataObject> dataObjects) {
        BlockServiceUtils.checkForPendingTasks(tenant, dataObjects, _dbClient);
    }
}
