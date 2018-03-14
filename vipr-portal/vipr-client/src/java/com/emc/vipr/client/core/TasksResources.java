/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.tasks.TaskBulkRep;
import com.emc.storageos.model.tasks.TaskStatsRestRep;
import com.emc.storageos.model.tasks.TasksList;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.client.system.Config;

/**
 * Tasks resources.
 * <p>
 * Base URL: <tt>/vdc/task</tt>
 */
public class TasksResources extends AbstractBulkResources<TaskResourceRep> {
    public static final URI SYSTEM_TENANT = URI.create("system");
    public static final URI USER_TENANT = null; // Null tells calls to not pass the user tenant

    public static final Integer FETCH_ALL = -1;

    public static final String TENANT_PARAM = "tenant";
    public static final String RESOURCE_PARAM = "resource";
    public static final String MAX_COUNT_PARAM = "max_count";
    public static final String START_TIME_PARAM = "startTime";
    public static final String END_TIME_PARAM = "endTime";
    public static final String STATE_PARAM = "state";
    public static final String CONFIG_TASK_MAX_COUNT = "task_max_count_display";

    public static enum State {
        PENDING("pending"),
        COMPLETED("completed"),
        ERROR("error");

        String literal;

        State(String literal) {
            this.literal = literal;
        }

        public String getLiteral() {
            return literal;
        }
    }

    public TasksResources(RestClient client) {
        super(client, TaskResourceRep.class, PathConstants.TASK_URL);
    }

    /**
     * @return All tasks in the users tenant
     */
    public List<NamedRelatedResourceRep> listAll() {
        return listByTenant(null, FETCH_ALL, null, null);
    }

    /**
     * List ALL tasks for a specific tenant (use {@link #SYSTEM_TENANT} for System level tasks).
     * 
     * Tasks are returned sorted with the most recent task first
     * 
     * @param tenantId The tenant tasks are required for
     */
    public List<NamedRelatedResourceRep> listByTenant(URI tenantId) {
        return listByTenant(tenantId, FETCH_ALL, null, null);
    }

    /**
     * List specified number of tasks for a specific tenant (use {@link #SYSTEM_TENANT} for System level tasks).
     * 
     * Tasks are returned sorted with the most recent task first
     * 
     * @param tenantId The tenant tasks are required for
     * @param maxCount The maximum number of tasks to return. Use {@link #FETCH_ALL} for all tasks
     */
    public List<NamedRelatedResourceRep> listByTenant(URI tenantId, int maxCount) {
        return listByTenant(tenantId, maxCount, null, null);
    }

    /**
     * List all tasks for a specific tenant (use {@link #SYSTEM_TENANT} for System level tasks) that were started between
     * startTime and endTime
     * 
     * Tasks are returned sorted with the most recent task first
     * 
     * @param tenantId The tenant tasks are required for
     * @param maxCount Number of tasks to return or {@link #FETCH_ALL} for all matching tasks
     * @param startTime Time in milliseconds (Null if not required)
     * @param endTime End time in milliseconds (Null if not required)
     */
    public List<NamedRelatedResourceRep> listByTenant(URI tenantId, int maxCount, Long startTime, Long endTime) {
        UriBuilder builder = client.uriBuilder(baseUrl);
        addTenant(builder, tenantId);
        builder.queryParam(MAX_COUNT_PARAM, maxCount);

        if (startTime != null) {
            builder.queryParam(START_TIME_PARAM, startTime);
        }

        if (endTime != null) {
            builder.queryParam(END_TIME_PARAM, endTime);
        }

        URI uri = builder.build();
        return client.resource(uri).get(TasksList.class).getTasks();
    }

    public List<TaskResourceRep> findByResource(URI resourceId) {
        List<SearchResultResourceRep> results = performSearchBy(RESOURCE_PARAM, resourceId);
        return getByRefs(results);
    }

    /**
     * Returns tasks matching a particular state for the given tenant
     */
    public List<TaskResourceRep> findByState(URI tenantId, State state) {
        Map<String, Object> params = new HashMap<>();

        params.put(TENANT_PARAM, tenantId);
        params.put(STATE_PARAM, state.getLiteral());

        List<SearchResultResourceRep> results = performSearch(params);
        return getByRefs(results);
    }

    /**
     * Returns the specified number of tasks that have been created for the given client since startTime
     * 
     * @param tenantId The tenant (use {@link #USER_TENANT} for current users tasks, or {@link #SYSTEM_TENANT} for system level tasks
     * @param startTime Number of milliseconds since Jan 1 (or Date().getTime())
     * @param maxCount Number of tasks to return or {@link #FETCH_ALL} for all matching tasks
     */
    public List<TaskResourceRep> findCreatedSince(URI tenantId, long startTime, int maxCount) {
    	//Retrieve the task max count value from the configuration properties
    	Config viprConfig = new Config(client);
    	String config_task_max_count = viprConfig.getProperties().getProperty(CONFIG_TASK_MAX_COUNT);
    	
        if (maxCount == 5) {
        	return getByRefs(listByTenant(tenantId, maxCount, startTime, null));
        } 
        if (config_task_max_count.equals("All")) {
        	maxCount = FETCH_ALL;
        } else {
        	maxCount = Integer.parseInt(config_task_max_count);
        }
        return getByRefs(listByTenant(tenantId, maxCount, null, null));
    }

    public TaskStatsRestRep getStats() {
        return getStatsByTenant(null);
    }

    /**
     * Delete a task
     * 
     * @param taskId task to delete
     */
    public void delete(URI taskId) {
        client.postURI(String.class, client.uriBuilder(getIdUrl() + "/delete").build(taskId));
    }

    /**
     * Resume a task
     * 
     * @param taskId task to resume
     */
    public void resume(URI taskId) {
        client.postURI(String.class, client.uriBuilder(getIdUrl() + "/resume").build(taskId));
    }

    /**
     * Rollback a task
     * 
     * @param taskId task to rollback
     */
    public void rollback(URI taskId) {
        client.postURI(String.class, client.uriBuilder(getIdUrl() + "/rollback").build(taskId));
    }

    /**
     * Returns task statistics for the given tenant
     */
    public TaskStatsRestRep getStatsByTenant(URI tenantId) {
        UriBuilder builder = client.uriBuilder(baseUrl + "/stats");
        addTenant(builder, tenantId);

        URI uri = builder.build();

        return client.resource(uri).get(TaskStatsRestRep.class);
    }

    @Override
    public TasksResources withInactive(boolean inactive) {
        return (TasksResources) super.withInactive(inactive);
    }

    @Override
    public TasksResources withInternal(boolean internal) {
        return (TasksResources) super.withInternal(internal);
    }

    /**
     * Gets a list of tasks for the specified URI
     */
    protected List<TaskResourceRep> getList(URI uri) {
        TaskList tasks = client.resource(uri).get(TaskList.class);
        return defaultList(tasks.getTaskList());
    }

    private void addTenant(UriBuilder builder, URI tenantId) {
        if (tenantId != null) {
            builder.queryParam(TENANT_PARAM, tenantId);
        }
    }

    private void addResource(UriBuilder builder, URI tenantId) {
        if (tenantId != null) {
            builder.queryParam(RESOURCE_PARAM, tenantId);
        }
    }

    @Override
    protected List<TaskResourceRep> getBulkResources(BulkIdParam input) {
        TaskBulkRep response = client.post(TaskBulkRep.class, input, getBulkUrl());
        return defaultList(response.getTasks());
    }
}
