/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.api.mapper.TaskMapper.toTaskList;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.impl.resource.utils.AsyncTaskExecutorService;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.security.authorization.InheritCheckPermission;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.base.Joiner;

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
     * Return a set of URIs referencing DataObject associated to the list of Tenants that have pending Tasks.
     * 
     * @param tenants - [in] List or Tenant URIs
     * @return Set or URIs referencing DataObjects that have pending Tasks against them
     */
    protected Set<URI> getObjectURIsThatHavePendingTasks(Collection<URI> tenants) {
        // Generate a set of Resource URIs that have pending Tasks against them
        Set<URI> urisHavingPendingTasks = new HashSet<>();
        for (URI tenant : tenants) {
            TaskUtils.ObjectQueryResult<Task> queryResult = TaskUtils.findTenantTasks(_dbClient, tenant);
            while (queryResult.hasNext()) {
                Task task = queryResult.next();
                if (task == null || task.getCompletedFlag() || task.getInactive()) {
                    continue;
                }
                if (task.isPending()) {
                    urisHavingPendingTasks.add(task.getResource().getURI());
                }
            }
        }

        return urisHavingPendingTasks;
    }

    /**
     * Given a list of Tenants and DataObject references, check if any of the DataObjects have pending
     * Tasks against them. If so, generate an error that this cannot be deleted.
     * 
     * @param tenants - in] List or Tenant URIs
     * @param dataObjects - [in] List of DataObjects to check
     */
    protected void checkForPendingTasks(Collection<URI> tenants, Collection<? extends DataObject> dataObjects) {
        Set<URI> objectURIsThatHavePendingTasks = getObjectURIsThatHavePendingTasks(tenants);

        // Search through the list of Volumes to see if any are in the pending list
        List<String> pendingObjectLabels = new ArrayList<>();
        for (DataObject dataObject : dataObjects) {
            if (dataObject.getInactive()) {
                continue;
            }
            String label = dataObject.getLabel();
            if (label == null) {
                label = dataObject.getId().toString();
            }
            if (objectURIsThatHavePendingTasks.contains(dataObject.getId())) {
                pendingObjectLabels.add(label);
                // Remove entry, since we already found it was matched.
                objectURIsThatHavePendingTasks.remove(dataObject.getId());
            }
        }

        // If there are an pendingObjectLabels, then we found some objects that have
        // a pending task against them. Need to signal an error
        if (!pendingObjectLabels.isEmpty()) {
            String pendingListStr = Joiner.on(',').join(pendingObjectLabels);
            _log.warn(String.format("Attempted to execute operation against these resources while there are tasks pending against them: %s",
                    pendingListStr));
            throw APIException.badRequests.cannotExecuteOperationWhilePendingTask(pendingListStr);
        }
    }
}
