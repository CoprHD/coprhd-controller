/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.refIds;
import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;
import static com.emc.vipr.client.core.impl.PathConstants.*;
import java.net.URI;
import java.util.*;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.TagAssignment;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.auth.*;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.search.SearchResults;
import com.emc.storageos.model.search.Tags;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.client.core.search.SearchBuilder;
import com.emc.vipr.client.core.util.ResourceUtils;
import javax.ws.rs.core.UriBuilder;

/**
 * Base class for all resource types. This class provides the implementation of many methods that may or may not be
 * implemented by all resources as a convenience (ACLs/Tasks/Quotas). This also provides a basic implementation of
 * {@link Resources#getByIds(Collection, ResourceFilter)} that looks up each resource in the collection one by one.
 * 
 * @param <T>
 *        the type of resource.
 */
public abstract class AbstractResources<T extends DataObjectRestRep> implements Resources<T> {
    
    protected final RestClient client;
    protected final Class<T> resourceClass;
    protected final String baseUrl;

    /** Whether to include inactive resources in fetch operations, defaults to false. */
    private boolean includeInactive;

    /** Whether to include internal resources in fetch operations, defaults to false. */
    private boolean includeInternal;

    public AbstractResources(RestClient client, Class<T> resourceClass, String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl;
        this.resourceClass = resourceClass;
    }

    /**
     * Configures the fetch operations to include inactive resources.
     * 
     * @param inactive
     *        whether to include inactive resources.
     * @return this AbstractResources.
     */
    public AbstractResources<T> withInactive(boolean inactive) {
        this.includeInactive = inactive;
        return this;
    }

    /**
     * Configures the fetch operations to include internal resources.
     *
     * @param internal
     *        whether to include internal resources.
     * @return this AbstractResources.
     */
    public AbstractResources<T> withInternal(boolean internal) {
        this.includeInternal = internal;
        return this;
    }
    
    /**
     * Gets the URL for selecting a resource by ID: <tt><i>baseUrl</i>/{id}</tt>
     * 
     * @return the ID URL.
     */
    protected String getIdUrl() {
        return String.format(ID_URL_FORMAT, baseUrl);
    }

    /**
     * Gets the URL for deactivating a resource by ID: <tt><i>baseUrl</i>/{id}/deactivate</tt>
     * 
     * @return the deactivate URL.
     */
    protected String getDeactivateUrl() {
        return String.format(DEACTIVATE_URL_FORMAT, baseUrl);
    }

    /**
     * Gets the URL for registering a resource by ID: <tt><i>baseUrl</i>/{id}/register</tt>
     *
     * @return the deactivate URL.
     */
    protected String getRegisterUrl() {
        return String.format(REGISTER_URL_FORMAT, baseUrl);
    }

    /**
     * Gets the URL for de-registering a resource by ID: <tt><i>baseUrl</i>/{id}/deregister</tt>
     *
     * @return the deactivate URL.
     */
    protected String getDeregisterUrl() {
        return String.format(DEREGISTER_URL_FORMAT, baseUrl);
    }

    /**
     * Gets the URL for getting/setting tags for a resource by ID: <tt><i>baseUrl</i>/{id}/tags</tt>
     * 
     * @return the tags URL.
     */
    protected String getTagsUrl() {
        return String.format(TAGS_URL_FORMAT, baseUrl);
    }

    /**
     * Gets the URL for getting/setting ACLs for a resource by ID: <tt><i>baseUrl</i>/{id}/acl</tt>
     * 
     * @return the ACLs URL.
     */
    protected String getAclUrl() {
        return String.format(ACL_URL_FORMAT, baseUrl);
    }

    /**
     * Gets the URL for getting/setting role assignments for a resource by ID:
     * <tt><i>baseUrl</i>/{id}/role-assignments</tt>
     * 
     * @return the role assignments URL.
     */
    protected String getRoleAssignmentsUrl() {
        return String.format(ID_URL_FORMAT + ROLE_ASSIGNMENT_PATH, baseUrl);
    }

    /**
     * Gets the URL for getting/setting quotas for a resource by ID: <tt><i>baseUrl</i>/{id}/quota</tt>
     * 
     * @return the quota URL.
     */
    protected String getQuotaUrl() {
        return String.format(QUOTA_URL_FORMAT, baseUrl);
    }

    /**
     * Gets the URL for searching for resources of this type: <tt><i>baseUrl</i>/search</tt>
     * 
     * @return the search URL.
     */
    protected String getSearchUrl() {
        return String.format(SEARCH_URL_FORMAT, baseUrl);
    }

    @Override
    public T get(URI id) {
        if (id != null) {
            return client.get(resourceClass, getIdUrl(), id);
        }
        else {
            return null;
        }
    }

    @Override
    public T get(RelatedResourceRep ref) {
        return (ref != null) ? get(ref.getId()) : null;
    }

    @Override
    public List<T> getByIds(Collection<URI> ids) {
        return getByIds(ids, null);
    }

    @Override
    public List<T> getByIds(Collection<URI> ids, ResourceFilter<T> filter) {
        List<T> results = new ArrayList<T>();
        if (ids != null) {
            for (URI id : ids) {
                if (!acceptId(id, filter)) {
                    continue;
                }

                T item = get(id);
                if ((item != null) && accept(item, filter)) {
                    results.add(item);
                }
            }
        }
        return results;
    }

    @Override
    public List<T> getByRefs(Collection<? extends RelatedResourceRep> resources) {
        return getByRefs(resources, null);
    }

    @Override
    public List<T> getByRefs(Collection<? extends RelatedResourceRep> refs, ResourceFilter<T> filter) {
        return getByIds(refIds(refs), filter);
    }

    @Override
    public Set<String> getTags(URI id) {
        Tags tags = client.get(Tags.class, getTagsUrl(), id);
        return tags != null ? tags.getTag() : new HashSet<String>();
    }

    @Override
    public void addTags(URI id, Set<String> add) {
        updateTags(id, new TagAssignment(add, new HashSet<String>()));
    }

    @Override
    public void removeTags(URI id, Set<String> remove) {
        updateTags(id, new TagAssignment(new HashSet<String>(), remove));
    }

    @Override
    public void updateTags(URI id, TagAssignment tags) {
        client.put(String.class, tags, getTagsUrl(), id);
    }

    /**
     * Deactivates a resource by ID. Some resource types return no result on deactivate.
     * 
     * @param id
     *        the ID of the resource to deactivate.
     */
    protected void doDeactivate(URI id) {
        client.post(String.class, getDeactivateUrl(), id);
    }

    /**
     * Deactivates a resource by ID and returns the deactivate task. Some resource types return a task when
     * deactivating.
     * 
     * @param id
     *        the ID of the resource to deactivate.
     * @return the deactivate task.
     */
    protected Task<T> doDeactivateWithTask(URI id) {
        return postTask(getDeactivateUrl(), id);
    }

    /**
     * Deactivates a resource by ID and returns the deactivate tasks. Some resource types return tasks when
     * deactivating.
     * 
     * @param id
     *        the ID of the resource to deactivate.
     * @return the deactivate tasks.
     */
    protected Tasks<T> doDeactivateWithTasks(URI id) {
        return postTasks(getDeactivateUrl(), id);
    }
    
    /**
     * Gets the tasks associated with a resource by ID (when supported).
     * <p>
     * API Call: GET <tt><i>baseUrl</i>/{id}/tasks</tt>
     * 
     * @param id
     *        the ID of the resource.
     * @return the tasks associated with the resource.
     */
    protected Tasks<T> doGetTasks(URI id) {
        TaskList response = client.get(TaskList.class, getIdUrl() + "/tasks", id);
        return new Tasks<T>(client, response.getTaskList(), resourceClass);
    }

    /**
     * Gets a single task for a resource by ID.
     * <p>
     * API Call: GET <tt><i>baseUrl</i>/{id}/tasks/{taskId}</tt>
     * 
     * @param id
     *        the ID of the resource.
     * @param taskId
     *        the ID of the task to retrieve.
     * @return the task.
     */
    protected Task<T> doGetTask(URI id, URI taskId) {
        TaskResourceRep response = client.get(TaskResourceRep.class, getIdUrl() + "/tasks/{taskId}", id, taskId);
        return new Task<T>(client, response, resourceClass);
    }

    /**
     * Get ACLs for resources that support it.
     * <p>
     * API Call: GET <tt><i>baseUrl</i>/{id}/acl</tt>
     * 
     * @param id
     *        the ID of the resource.
     * @return the list of ACL entries.
     */
    protected List<ACLEntry> doGetACLs(URI id) {
        ACLAssignments response = client.get(ACLAssignments.class, getAclUrl(), id);
        return defaultList(response.getAssignments());
    }

    /**
     * Update ACLs for resource that support it.
     * <p>
     * API Call: PUT <tt><i>baseUrl</i>/{id}/acl</tt>
     * 
     * @param id
     *        the ID of the resource.
     * @param aclChanges
     *        the ACL changes.
     * @return the resulting list of ACL entries.
     */
    protected List<ACLEntry> doUpdateACLs(URI id, ACLAssignmentChanges aclChanges) {
        ACLAssignments response = client.put(ACLAssignments.class, aclChanges, getAclUrl(), id);
        return defaultList(response.getAssignments());
    }

    /**
     * Gets the quota info for resources that support it.
     * <p>
     * API Call: GET <tt><i>baseUrl</i>/{id}/quota</tt>
     * 
     * @param id
     *        the ID of the resource.
     * @return the quota info.
     */
    protected QuotaInfo doGetQuota(URI id) {
        return client.get(QuotaInfo.class, getQuotaUrl(), id);
    }

    /**
     * Updates the quota info for resources that support it.
     * <p>
     * API Call: PUT <tt><i>baseUrl</i>/{id}/quota</tt>
     * 
     * @param id
     *        the ID of the resource.
     * @param quota
     *        the quota update information.
     * @return the quota info.
     */
    protected QuotaInfo doUpdateQuota(URI id, QuotaUpdateParam quota) {
        return client.put(QuotaInfo.class, quota, getQuotaUrl(), id);
    }

    /**
     * Performs a POST with no request that will return a single task as a response.
     * 
     * @param path
     *        the path to post to.
     * @param args
     *        the path arguments.
     * @return the task object.
     */
    protected Task<T> postTask(String path, Object... args) {
        TaskResourceRep task = client.post(TaskResourceRep.class, path, args);
        return new Task<T>(client, task, resourceClass);
    }

    /**
     * Performs a POST with a request that will return a single task as a response.
     * 
     * @param request
     *        the request object.
     * @param path
     *        the path to post to.
     * @param args
     *        the path arguments.
     * @return the task object.
     */
    protected Task<T> postTask(Object request, String path, Object... args) {
        TaskResourceRep task = client.post(TaskResourceRep.class, request, path, args);
        return new Task<T>(client, task, resourceClass);
    }

    /**
     * Performs a POST with no request and returns a single task as a response.
     * 
     * @param uri
     *        the URI to post to.
     * @return the task object.
     */
    protected Task<T> postTaskURI(URI uri) {
        TaskResourceRep task = client.postURI(TaskResourceRep.class, uri);
        return new Task<T>(client, task, resourceClass);
    }


    /**
     * Performs a DELETE with no request and returns a single task as a response.
     *
     * @param uri
     *        the URI to post to.
     * @return the task object.
     */
    protected Task<T> deleteTaskURI(URI uri) {
        TaskResourceRep task = client.deleteURI(TaskResourceRep.class, uri);
        return new Task<T>(client, task, resourceClass);
    }

    /**
     * Performs a DELETE with a request that will return a single task as a response.
     *
     * @param path
     *        the path to post to.
     * @param args
     *        the path arguments.
     * @return the task object.
     */
    protected Task<T> deleteTask(String path, Object... args) {
        TaskResourceRep task = client.delete(TaskResourceRep.class, path, args);
        return new Task<T>(client, task, resourceClass);
    }

    /**
     * Performs a POST with a request that will return a single task as a response.
     * 
     * @param request
     *        the request object.
     * @param uri
     *        the URI to post to.
     * @return the task object.
     */
    protected Task<T> postTaskURI(Object request, URI uri) {
        TaskResourceRep task = client.postURI(TaskResourceRep.class, request, uri);
        return new Task<T>(client, task, resourceClass);
    }

    /**
     * Performs a PUT with a request that will return a single task as a response.
     * 
     * @param request
     *        the request object.
     * @param path
     *        the path to put to.
     * @param args
     *        the path arguments.
     * @return the task object.
     */
    protected Task<T> putTask(Object request, String path, Object... args) {
        TaskResourceRep task = client.put(TaskResourceRep.class, request, path, args);
        return new Task<T>(client, task, resourceClass);
    }
    
    /**
     * Performs a PUT with a request that will return a single task as a response.
     * 
     * @param request
     *        the request object.
     * @param uri
     *        the URI to put to.
     * @return the task object.
     */    
    protected Task<T> putTaskURI(Object request, URI uri) {
        TaskResourceRep task = client.putURI(TaskResourceRep.class, request, uri);
        return new Task<T>(client, task, resourceClass);
    }    

    /**
     * Performs a POST with no request that will return multiple tasks as a response.
     * 
     * @param path
     *        the path to post to.
     * @param args
     *        the path arguments.
     * @return the tasks object.
     */
    protected Tasks<T> postTasks(String path, Object... args) {
        TaskList tasks = client.post(TaskList.class, path, args);
        return new Tasks<T>(client, tasks.getTaskList(), resourceClass);
    }

    /**
     * Performs a POST with a request that will return multiple tasks as a response.
     * 
     * @param request
     *        the request object.
     * @param path
     *        the path to post to.
     * @param args
     *        the path arguments.
     * @return the tasks object.
     */
    protected Tasks<T> postTasks(Object request, String path, Object... args) {
        TaskList tasks = client.post(TaskList.class, request, path, args);
        return new Tasks<T>(client, tasks.getTaskList(), resourceClass);
    }

    /**
     * Performs a POST with a request that will return multiple tasks as a response.
     * 
     * @param request
     *        the request object.
     * @param uri
     *        the URI to post to.
     * @return the tasks object.
     */
    protected Tasks<T> postTasksURI(Object request, URI uri) {
        TaskList tasks = client.postURI(TaskList.class, request, uri);
        return new Tasks<T>(client, tasks.getTaskList(), resourceClass);
    }

    /**
     * Determines if the ID is accepted by the filter.
     * 
     * @param id
     *        the ID.
     * @param filter
     *        the filter (may be null for no filtering).
     * @return true if the ID is accepted.
     */
    protected <V extends DataObjectRestRep> boolean acceptId(URI id, ResourceFilter<V> filter) {
        if (id == null) {
            return false;
        }
        if (filter != null) {
            return filter.acceptId(id);
        }
        return true;
    }

    /**
     * Determines if the item is accepted.
     * 
     * @param item
     *        the item.
     * @param filter
     *        the filter (may be null for no filtering).
     * @return true if the item is accepted.
     */
    protected <V extends DataObjectRestRep> boolean accept(V item, ResourceFilter<V> filter) {
        // Filter inactive
        if (!includeInactive && !ResourceUtils.isActive(item)) {
            return false;
        }
        // Filter internal
        if (!includeInternal && !ResourceUtils.isNotInternal(item)) {
            return false;
        }
        if (filter != null) {
            return filter.accept(item);
        }
        return true;
    }

    @Override
    public SearchBuilder<T> search() {
        return new SearchBuilder<T>(this);
    }

    /**
     * Performs a search for resources matching the a single search parameter.
     * 
     * @param name
     *        the parameter name.
     * @param value
     *        the parameter value.
     * @return the list of resources.
     */
    public List<SearchResultResourceRep> performSearchBy(String name, Object value) {
        Map<String, Object> params = Collections.singletonMap(name, value);
        return performSearch(params);
    }

    /**
     * Performs a search for resources matching the given parameters.
     * 
     * @param params
     *        the search query parameters.
     * @return the list of resources.
     */
    public List<SearchResultResourceRep> performSearch(Map<String, Object> params) {
        UriBuilder builder = client.uriBuilder(getSearchUrl());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            builder.queryParam(entry.getKey(), entry.getValue());
        }
        SearchResults searchResults = client.getURI(SearchResults.class, builder.build());
        List<SearchResultResourceRep> results = searchResults.getResource();
        if (results == null) {
            results = new ArrayList<SearchResultResourceRep>();
        }
        return results;
    }
}
