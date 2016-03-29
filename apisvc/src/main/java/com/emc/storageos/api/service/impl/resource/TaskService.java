/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.mapper.functions.MapTask;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.db.client.TimestampedURIQueryResult;
import com.emc.storageos.db.client.constraint.AggregatedConstraint;
import com.emc.storageos.db.client.constraint.AggregationQueryResultList;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.model.*;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.search.SearchResults;
import com.emc.storageos.model.search.Tags;
import com.emc.storageos.model.tasks.TaskBulkRep;
import com.emc.storageos.model.tasks.TaskStatsRestRep;
import com.emc.storageos.model.tasks.TasksList;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Path("/vdc/tasks")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN, Role.TENANT_ADMIN })
public class TaskService extends TaggedResource {

    Logger log = LoggerFactory.getLogger(TaskService.class.getName());

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd_HH:mm:ss";

    private static final URI SYSTEM_TENANT = URI.create("system");
    private static final Integer FETCH_ALL = -1;

    private static final String TENANT_QUERY_PARAM = "tenant";
    private static final String RESOURCE_QUERY_PARAM = "resource";
    private static final String MAX_COUNT_PARAM = "max_count";
    private static final String START_TIME = "startTime";
    private static final String END_TIME = "endTime";
    private static final String STATE_PARAM = "state";

    /**
     * Returns information about the specified task.
     * 
     * @param id the URN of a ViPR task
     * @brief Show Task
     * @return The specified task details
     */
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public TaskResourceRep getTask(@PathParam("id") URI id) {
        Task task = queryResource(id);

        // Permission Check
        if (task.getTenant().equals(TenantOrg.SYSTEM_TENANT)) {
            verifySystemAdmin();
        }
        else {
            verifyUserHasAccessToTenants(Lists.newArrayList(task.getTenant()));
        }

        return TaskMapper.toTask(task);
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @prereq none
     * 
     * @param param POST data containing the id list.
     * 
     * @brief List data of volume resources
     * @return list of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public TaskBulkRep getBulkResources(BulkIdParam param) {
        return (TaskBulkRep) super.getBulkResources(param);
    }

    /**
     * Returns task status count information for the specified Tenant.
     * 
     * @brief Task Status count
     * @param tenantId Tenant URI of the tenant the count is required for. If not supplied, the logged in users tenant will be used.
     *            A value of 'system' will return system tasks
     * @return Count of tasks in different statuses
     */
    @GET
    @Path("/stats")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public TaskStatsRestRep getStats(@QueryParam(TENANT_QUERY_PARAM) URI tenantId) {

        log.info("enter task stat");

        Set<URI> tenantIds = getTenantsFromRequest(tenantId);
        verifyUserHasAccessToTenants(tenantIds);

        log.info("finish tenant job");

        int ready = 0;
        int error = 0;
        int pending = 0;
        for (URI normalizedTenantId : tenantIds) {
            Constraint constraint = AggregatedConstraint.Factory.getAggregationConstraint(Task.class, "tenant",
                    normalizedTenantId.toString(), "taskStatus");
            AggregationQueryResultList queryResults = new AggregationQueryResultList();

            log.info("before query task stat from db");
            _dbClient.queryByConstraint(constraint, queryResults);
            log.info("after query task stat from db");

            Iterator<AggregationQueryResultList.AggregatedEntry> it = queryResults.iterator();
            while (it.hasNext()) {
                AggregationQueryResultList.AggregatedEntry entry = it.next();
                if (entry.getValue().equals(Task.Status.ready.name())) {
                    ready++;
                } else if (entry.getValue().equals(Task.Status.error.name())) {
                    error++;
                } else {
                    pending++;
                }
            }
        }

        log.info("leave task stat");
        return new TaskStatsRestRep(pending, ready, error);
    }

    /**
     * Returns a list of tasks for the specified tenant
     * 
     * @brief Return a list of tasks for a tenant
     * @param tenantId Tenant URI of the tenant the count is required for. If not supplied, the logged in users tenant will be used.
     *            A value of 'system' will provide a list of all the system tasks
     * @return A list of tasks for the tenant
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public TasksList getTasks(@QueryParam(TENANT_QUERY_PARAM) URI tenantId,
            @QueryParam(START_TIME) String startTime,
            @QueryParam(END_TIME) String endTime,
            @QueryParam(MAX_COUNT_PARAM) Integer max_count) {

        Set<URI> tenantIds = getTenantsFromRequest(tenantId);
        verifyUserHasAccessToTenants(tenantIds);

        // Entries from the index, sorted with most recent first
        Set<TimestampedURIQueryResult.TimestampedURI> sortedIndexEntries = Sets
                .newTreeSet(new Comparator<TimestampedURIQueryResult.TimestampedURI>() {
                    public int compare(TimestampedURIQueryResult.TimestampedURI obj1, TimestampedURIQueryResult.TimestampedURI obj2) {
                        if (Objects.equals(obj1.getTimestamp(), obj2.getTimestamp())) {
                            return 1; // If timestampe are equal don't return 0 or TreeSet will remove one of them
                        } else {
                            return obj2.getTimestamp().compareTo(obj1.getTimestamp());
                        }
                    }
                });

        Date startWindowDate = getDateFromString(startTime);
        Date endWindowDate = getDateFromString(endTime);

        // Fetch index entries and load into sorted set
        List<NamedRelatedResourceRep> resourceReps = Lists.newArrayList();
        for (URI normalizedTenantId : tenantIds) {
            TimestampedURIQueryResult taskIds = new TimestampedURIQueryResult();
            _dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getTimedTenantOrgTaskConstraint(normalizedTenantId, startWindowDate, endWindowDate),
                    taskIds);

            Iterator<TimestampedURIQueryResult.TimestampedURI> it = taskIds.iterator();
            while (it.hasNext()) {
                TimestampedURIQueryResult.TimestampedURI timestampedURI = it.next();
                sortedIndexEntries.add(timestampedURI);
            }
        }

        if (max_count == null || max_count < 0) {
            max_count = FETCH_ALL;
        }
        else {
            max_count = Math.min(max_count, sortedIndexEntries.size());
        }

        // Produce the requested number of results
        Iterator<TimestampedURIQueryResult.TimestampedURI> it = sortedIndexEntries.iterator();
        int pos = 0;
        while (it.hasNext() && (max_count == FETCH_ALL || pos < max_count)) {
            TimestampedURIQueryResult.TimestampedURI uri = it.next();

            RestLinkRep link = new RestLinkRep("self", RestLinkFactory.newLink(ResourceTypeEnum.TASK, uri.getUri()));
            resourceReps.add(new NamedRelatedResourceRep(uri.getUri(), link, uri.getName()));

            pos++;
        }

        return new TasksList(resourceReps);
    }

    /**
     * Deletes the specified task. After this operation has been called, the task will no longer be accessible.
     * 
     * @brief Deletes a task
     * @param taskId ID of the task to be deleted
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{taskId}/delete")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN }, acls = { ACL.OWN })
    public Response deleteTask(@PathParam("taskId") URI taskId) {
        Task task = queryResource(taskId);

        // Permission Check
        if (task.getTenant().equals(TenantOrg.SYSTEM_TENANT)) {
            verifySystemAdmin();
        }
        else {
            verifyUserHasAccessToTenants(Lists.newArrayList(task.getTenant()));
        }

        _dbClient.removeObject(task);
        auditOp(OperationTypeEnum.DELETE_TASK, true, null, task.getId().toString(), task.getLabel());

        return Response.ok().build();
    }

    /**
     * @brief Assign tags to resource
     *        Assign tags
     * 
     * @prereq none
     * 
     * @param id the URN of a ViPR resource
     * @param assignment tag assignments
     * @return No data returned in response body
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/tags")
    @Override
    public Tags assignTags(@PathParam("id") URI id, TagAssignment assignment) {
        Task task = queryResource(id);

        verifyUserHasAccessToTenants(Collections.singletonList(task.getTenant()));
        return super.assignTags(id, assignment);
    }

    /**
     * @brief List tags assigned to resource
     *        Returns assigned tags
     * 
     * @prereq none
     * 
     * @param id the URN of a ViPR Resource
     * @return Tags information
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/tags")
    @Override
    public Tags getTags(@PathParam("id") URI id) {
        Task task = queryResource(id);

        verifyUserHasAccessToTenants(Collections.singletonList(task.getTenant()));
        return super.getTags(id);
    }

    protected SearchResults getOtherSearchResults(Map<String, List<String>> parameters, boolean authorized) {
        SearchResults searchResults = new SearchResults();

        Set<URI> tenantIds = getTenantIdsFromParams(parameters);

        // Resource Query
        if (parameters.containsKey(RESOURCE_QUERY_PARAM)) {
            URI resourceId = URI.create(parameters.get(RESOURCE_QUERY_PARAM).get(0));

            List<NamedURI> tasks = TaskUtils.findResourceTaskIds(_dbClient, resourceId);

            if (!tasks.isEmpty()) {
                // All the tasks will have the same TenantID as the Resource
                Task task = queryResource(tasks.get(0).getURI());
                verifyUserHasAccessToTenants(Collections.singletonList(task.getTenant()));
            }

            searchResults.getResource().addAll(toSearchResults(tasks));

        } else if (parameters.containsKey(STATE_PARAM)) {
            // Search by task state
            String state = getStringParam(STATE_PARAM, parameters);

            if (state != null) {
                for (URI tenant : tenantIds) {
                    TaskUtils.ObjectQueryResult<Task> taskResult = TaskUtils.findTenantTasks(_dbClient, tenant);

                    while (taskResult.hasNext()) {
                        Task task = taskResult.next();

                        if (task.getStatus().equals(state)) {
                            searchResults.getResource().add(toSearchResult(task.getId()));
                        }
                    }
                }
            }
        }

        return searchResults;
    }

    protected Task queryResource(URI id) {
        ArgValidator.checkUri(id);
        Task task = _dbClient.queryObject(Task.class, id);
        ArgValidator.checkEntityNotNull(task, id, isIdEmbeddedInURL(id));

        return task;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.TASK;
    }

    @Override
    public Class<Task> getResourceClass() {
        return Task.class;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        Task task = queryResource(id);
        return task.getTenant();
    }

    @Override
    protected ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper) {
        return new TaskResRepFilter(user, permissionsHelper);
    }

    private Set<URI> getTenantIdsFromParams(Map<String, List<String>> parameters) {
        if (!parameters.containsKey(TENANT_QUERY_PARAM) ||
                parameters.get(TENANT_QUERY_PARAM).isEmpty()) {
            return getTenantsFromRequest(null);
        }

        return getTenantsFromRequest(URI.create(parameters.get(TENANT_QUERY_PARAM).get(0)));
    }

    private String getStringParam(String name, Map<String, List<String>> parameters) {
        List<String> values = parameters.get(name);

        if (!values.isEmpty()) {
            return values.get(0);
        }
        else {
            return null;
        }
    }

    /**
     * Processes the tenant id that the user provided and returns a list of tenant IDs that match the request
     */
    private Set<URI> getTenantsFromRequest(URI requestedTenantId) {
        Set<URI> tenants = Sets.newHashSet();

        if (requestedTenantId == null) {
            // No tenant specified, so return users home tenant and subtenants
            tenants.add(URI.create(getUserFromContext().getTenantId()));

            Map<String, Collection<String>> subTenantRoles = _permissionsHelper.getSubtenantRolesForUser(getUserFromContext());
            for (Map.Entry<String, Collection<String>> subTenant : subTenantRoles.entrySet()) {
                if (hasTenantAdmin(subTenant.getValue())) {
                    tenants.add(URI.create(subTenant.getKey()));
                }
            }
        }
        else if (Objects.equals(requestedTenantId, SYSTEM_TENANT) || Objects.equals(requestedTenantId, TenantOrg.SYSTEM_TENANT)) {
            tenants.add(TenantOrg.SYSTEM_TENANT);
        }
        else {
            tenants.add(requestedTenantId);
        }

        return tenants;
    }

    /**
     * Verifies that the user has permission to access all the tenants in the tenants collection
     */
    private void verifyUserHasAccessToTenants(Collection<URI> tenants) {
        StorageOSUser user = getUserFromContext();
        if (_permissionsHelper.userHasGivenRole(user, URI.create(user.getTenantId()), Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN,
                Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN)) {
            return;
        }

        Set<String> subtenants = _permissionsHelper.getSubtenantRolesForUser(user).keySet();

        for (URI tenantId : tenants) {
            if (tenantId.equals(TenantOrg.SYSTEM_TENANT)) {
                verifySystemAdmin();
            }
            else if (!tenantId.toString().equals(user.getTenantId()) &&
                    !subtenants.contains(tenantId.toString())) {
                throw APIException.forbidden
                        .insufficientPermissionsForUser(user.getName());
            }
        }
    }

    private boolean hasTenantAdmin(Collection<String> roles) {
        for (String role : roles) {
            if (role.equals(Role.TENANT_ADMIN.name())) {
                return true;
            }
        }

        return false;
    }

    /** @return a List of search results from the provided set of items, filtered to only those items the user has access to */
    private List<SearchResultResourceRep> toTenantFilteredSearchResults(Set<URI> tenantIds, NamedElementQueryResultList items) {
        List<SearchResultResourceRep> results = Lists.newArrayList();

        Iterator<NamedElementQueryResultList.NamedElement> it = items.iterator();
        while (it.hasNext()) {
            NamedElementQueryResultList.NamedElement item = it.next();

            Task task = _dbClient.queryObject(Task.class, item.getId());
            if (task.getTenant() != null && tenantIds.contains(task.getTenant())) {
                RestLinkRep selfLink = new RestLinkRep("self", RestLinkFactory.newLink(getResourceType(), item.getId()));
                results.add(new SearchResultResourceRep(item.getId(), selfLink, null));
            }
        }

        return results;
    }

    private List<SearchResultResourceRep> toSearchResults(List<NamedURI> items) {
        List<SearchResultResourceRep> results = Lists.newArrayList();

        for (NamedURI item : items) {
            results.add(toSearchResult(item.getURI()));
        }

        return results;
    }

    private SearchResultResourceRep toSearchResult(URI uri) {
        RestLinkRep selfLink = new RestLinkRep("self", RestLinkFactory.newLink(getResourceType(), uri));
        return new SearchResultResourceRep(uri, selfLink, null);
    }

    private static Date getDateTimestampParam(List<String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }

        String timestampStr = parameters.get(0);
        if (StringUtils.isBlank(timestampStr)) {
            return null;
        }

        return getDateFromString(timestampStr);
    }

    private static Date getDateFromString(String timestampStr) {
        if (timestampStr == null) {
            return null;
        }

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_TIME_FORMAT);
            return dateFormat.parse(timestampStr);
        } catch (ParseException pe) {
            try {
                return new Date(Long.parseLong(timestampStr));
            } catch (NumberFormatException n) {
                throw APIException.badRequests.invalidDate(timestampStr);
            }
        }
    }

    /**
     * Retrieve task representations based on input ids.
     * 
     * @return list of task representations.
     */
    @Override
    public TaskBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<Task> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new TaskBulkRep(BulkList.wrapping(_dbIterator, MapTask.getInstance()));
    }

    @Override
    public String getServiceType() {
        return "Task";
    }

    @Override
    protected BulkRestRep queryFilteredBulkResourceReps(List<URI> ids) {
        Iterator<Task> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);

        BulkList.TaskFilter filter = new BulkList.TaskFilter(getUserFromContext(), _permissionsHelper);
        return new TaskBulkRep(BulkList.wrapping(_dbIterator, MapTask.getInstance(), filter));
    }

    public static class TaskResRepFilter<E extends RelatedResourceRep> extends ResRepFilter<E> {
        public TaskResRepFilter(StorageOSUser user, PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(E resourceRep) {
            Task task = _permissionsHelper.getObjectById(resourceRep.getId(), Task.class);
            if (task == null) {
                return false;
            }
            if (task.getTenant().toString().equals(_user.getTenantId())) {
                return true;
            }

            return isTenantAccessible(task.getTenant());
        }
    }
}
