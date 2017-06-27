/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.mapper.EventMapper;
import com.emc.storageos.api.mapper.functions.MapEvent;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.computesystemcontroller.ComputeSystemController;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AggregatedConstraint;
import com.emc.storageos.db.client.constraint.AggregationQueryResultList;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.util.EventUtils;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.event.EventBulkRep;
import com.emc.storageos.model.event.EventDetailsRestRep;
import com.emc.storageos.model.event.EventList;
import com.emc.storageos.model.event.EventRestRep;
import com.emc.storageos.model.event.EventStatsRestRep;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.search.SearchResults;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.collect.Lists;

/**
 * A service that provides APIs for viewing, approving, declining and removing actionable events.
 */
@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN }, writeRoles = {
        Role.TENANT_ADMIN }, readAcls = { ACL.ANY })
@Path("/vdc/events")
public class EventService extends TaggedResource {

    protected final static Logger _log = LoggerFactory.getLogger(EventService.class);

    private static final String EVENT_SERVICE_TYPE = "event";
    private static final String TENANT_QUERY_PARAM = "tenant";

    private static final String RESOURCE_QUERY_PARAM = "resource";

    private static final String DETAILS_SUFFIX = "Details";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * Get Event
     * 
     * @param id
     * @brief Show details for a specified event
     * @return
     * @throws DatabaseException
     */
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public EventRestRep getEvent(@PathParam("id") URI id) throws DatabaseException {
        ActionableEvent event = queryObject(ActionableEvent.class, id, false);
        // check the user permissions
        verifyAuthorizedInTenantOrg(event.getTenant(), getUserFromContext());
        return EventMapper.map(event);
    }

    /**
     * Delete Event
     * 
     * @param id
     * @brief Delete an event
     * @return
     * @throws DatabaseException
     */
    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public Response deleteEvent(@PathParam("id") URI id) throws DatabaseException {
        ActionableEvent event = queryObject(ActionableEvent.class, id, false);
        // check the user permissions
        verifyAuthorizedInTenantOrg(event.getTenant(), getUserFromContext());
        _dbClient.markForDeletion(event);
        _log.info(
                "Deleting Actionable Event: " + event.getId() + " Tenant: " + event.getTenant() + " Description: " + event.getDescription()
                        + " Warning: " + event.getWarning()
                        + " Event Status: " + event.getEventStatus() + " Resource: " + event.getResource() + " Event Code: "
                        + event.getEventCode());
        return Response.ok().build();
    }

    /**
     * Approve Event
     * 
     * @param id
     * @brief Change an event to approved
     * @return
     * @throws DatabaseException
     */
    @POST
    @Path("/{id}/approve")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public TaskList approveEvent(@PathParam("id") URI id) throws DatabaseException {
        ActionableEvent event = queryObject(ActionableEvent.class, id, false);
        verifyAuthorizedInTenantOrg(event.getTenant(), getUserFromContext());

        if (!StringUtils.equalsIgnoreCase(event.getEventStatus(), ActionableEvent.Status.pending.name())
                && !StringUtils.equalsIgnoreCase(event.getEventStatus(), ActionableEvent.Status.failed.name())) {
            throw APIException.badRequests.eventCannotBeApproved(event.getEventStatus());
        }

        _log.info(
                "Approving Actionable Event: " + event.getId() + " Tenant: " + event.getTenant() + " Description: " + event.getDescription()
                        + " Warning: " + event.getWarning()
                        + " Event Status: " + event.getEventStatus() + " Resource: " + event.getResource() + " Event Code: "
                        + event.getEventCode());

        return executeEventMethod(event, true);
    }

    /**
     * Event approval/decline details
     * 
     * @param id
     * @brief Show approve/decline details for an event
     * @return
     * @throws DatabaseException
     */
    @GET
    @Path("/{id}/details")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public EventDetailsRestRep eventDetails(@PathParam("id") URI id) throws DatabaseException {
        ActionableEvent event = queryObject(ActionableEvent.class, id, false);
        verifyAuthorizedInTenantOrg(event.getTenant(), getUserFromContext());
        EventDetailsRestRep eventDetails = new EventDetailsRestRep();
        eventDetails.setApproveDetails(getEventDetails(event, true));
        eventDetails.setDeclineDetails(getEventDetails(event, false));
        return eventDetails;
    }

    /**
     * Gets details for an event
     * 
     * @param event the event to get details for
     * @param approve if true, get the approve details, if false the get the decline details
     * @return event details
     */
    public List<String> getEventDetails(ActionableEvent event, boolean approve) {

        byte[] method = approve ? event.getApproveMethod() : event.getDeclineMethod();

        if (method == null || method.length == 0) {
            _log.info("Method is null or empty for event " + event.getId());
            return Lists.newArrayList("N/A");
        }

        ActionableEvent.Method eventMethod = ActionableEvent.Method.deserialize(method);
        if (eventMethod == null) {
            _log.info("Event method is null or empty for event " + event.getId());
            return Lists.newArrayList("N/A");
        }

        String eventMethodName = eventMethod.getMethodName() + DETAILS_SUFFIX;

        try {
            Method classMethod = getMethod(ActionableEventExecutor.class, eventMethodName);
            if (classMethod == null) {
                return Lists.newArrayList("N/A");
            } else {
                ComputeSystemController controller = getController(ComputeSystemController.class, null);
                ActionableEventExecutor executor = new ActionableEventExecutor(_dbClient, controller);
                return (List<String>) classMethod.invoke(executor, eventMethod.getArgs());
            }
        } catch (SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            _log.error(e.getMessage(), e.getCause());
            throw APIException.badRequests.errorInvokingEventMethod(event.getId(), eventMethodName);
        }
    }

    /**
     * Executes an actionable event method
     * 
     * @param event the event to execute
     * @param approve if true, the action is to approve, if false the action is to decline
     * @return list of tasks
     */
    public TaskList executeEventMethod(ActionableEvent event, boolean approve) {
        TaskList taskList = new TaskList();

        byte[] method = approve ? event.getApproveMethod() : event.getDeclineMethod();
        String eventStatus = approve ? ActionableEvent.Status.approved.name() : ActionableEvent.Status.declined.name();

        event.setEventExecutionTime(Calendar.getInstance());
        event.setApproveDetails(new StringSet(getEventDetails(event, true)));
        event.setDeclineDetails(new StringSet(getEventDetails(event, false)));

        if (method == null || method.length == 0) {
            _log.info("Method is null or empty for event " + event.getId());
            event.setEventStatus(eventStatus);
            _dbClient.updateObject(event);
            return taskList;
        }

        ActionableEvent.Method eventMethod = ActionableEvent.Method.deserialize(method);
        if (eventMethod == null) {
            _log.info("Event method is null or empty for event " + event.getId());
            event.setEventStatus(eventStatus);
            _dbClient.updateObject(event);
            return taskList;
        }

        try {
            Method classMethod = getMethod(ActionableEventExecutor.class, eventMethod.getMethodName());
            ComputeSystemController controller = getController(ComputeSystemController.class, null);
            ActionableEventExecutor executor = new ActionableEventExecutor(_dbClient, controller);
            Object[] parameters = Arrays.copyOf(eventMethod.getArgs(), eventMethod.getArgs().length + 1);
            parameters[parameters.length - 1] = event.getId();
            event.setEventStatus(eventStatus);
            _dbClient.updateObject(event); 
            TaskResourceRep result = (TaskResourceRep) classMethod.invoke(executor, parameters);
            if (result != null && result.getId() != null) {
                Collection<String> taskCollection = Lists.newArrayList(result.getId().toString());
                ActionableEvent updatedEvent = _dbClient.queryObject(ActionableEvent.class, event.getId());
                updatedEvent.setTaskIds(new StringSet(taskCollection));
                _dbClient.updateObject(updatedEvent);
            }
            taskList.addTask(result);
            return taskList;
        } catch (SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            _log.error(e.getMessage(), e.getCause());
            throw APIException.badRequests.errorInvokingEventMethod(event.getId(), eventMethod.getMethodName());
        }
    }

    /**
     * Returns a reference to a method for the given class with the given name
     * 
     * @param clazz class which the method belongs
     * @param name the name of the method
     * @return method or null if it doesn't exist
     */
    private Method getMethod(Class clazz, String name) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equalsIgnoreCase(name)) {
                return method;
            }
        }
        return null;
    }

    /**
     * Decline Event
     * 
     * @param id
     * @brief Change an event to declined 
     * @return
     * @throws DatabaseException
     */
    @POST
    @Path("/{id}/decline")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public TaskList declineEvent(@PathParam("id") URI id) throws DatabaseException {
        ActionableEvent event = queryObject(ActionableEvent.class, id, false);
        verifyAuthorizedInTenantOrg(event.getTenant(), getUserFromContext());

        if (!StringUtils.equalsIgnoreCase(event.getEventStatus(), ActionableEvent.Status.pending.name())
                && !StringUtils.equalsIgnoreCase(event.getEventStatus(), ActionableEvent.Status.failed.name())) {
            throw APIException.badRequests.eventCannotBeDeclined(event.getEventStatus());
        }

        _log.info(
                "Declining Actionable Event: " + event.getId() + " Tenant: " + event.getTenant() + " Description: " + event.getDescription()
                        + " Warning: " + event.getWarning()
                        + " Event Status: " + event.getEventStatus() + " Resource: " + event.getResource() + " Event Code: "
                        + event.getEventCode());

        return executeEventMethod(event, false);
    }

    /**
     * Retrieve resource representations based on input ids.
     *
     * @param param POST data containing the id list.
     * @brief List data of event resources
     * @return list of representations.
     *
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public EventBulkRep getBulkResources(BulkIdParam param) {
        return (EventBulkRep) super.getBulkResources(param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<ActionableEvent> getResourceClass() {
        return ActionableEvent.class;
    }

    @Override
    public EventBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<ActionableEvent> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new EventBulkRep(BulkList.wrapping(_dbIterator, MapEvent.getInstance()));
    }

    @Override
    public EventBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        Iterator<ActionableEvent> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter filter = new BulkList.EventFilter(getUserFromContext(), _permissionsHelper);
        return new EventBulkRep(BulkList.wrapping(_dbIterator, MapEvent.getInstance(), filter));
    }

    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected boolean isSysAdminReadableResource() {
        return true;
    }

    /**
     * List Events
     * 
     * @param tid
     * @brief List events for the queried tenant.
     * @return
     * @throws DatabaseException
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public EventList listEvents(@QueryParam("tenant") final URI tid) throws DatabaseException {
        URI tenantId;
        StorageOSUser user = getUserFromContext();
        if (tid == null || StringUtils.isBlank(tid.toString())) {
            tenantId = URI.create(user.getTenantId());
        } else {
            tenantId = tid;
        }
        // this call validates the tenant id
        TenantOrg tenant = _permissionsHelper.getObjectById(tenantId, TenantOrg.class);
        ArgValidator.checkEntity(tenant, tenantId, isIdEmbeddedInURL(tenantId), true);

        // check the user permissions for this tenant org
        verifyAuthorizedInTenantOrg(tenantId, user);
        // get all host children
        EventList list = new EventList();
        list.setEvents(DbObjectMapper.map(ResourceTypeEnum.EVENT, listChildren(tenantId, ActionableEvent.class, "label", "tenant")));
        return list;
    }

    /**
     * Get Stats
     * 
     * @param tenantId
     * @brief Show numbers of pending, approved, declined, and failed events for a tenant
     * @return
     */
    @GET
    @Path("/stats")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public EventStatsRestRep getStats(@QueryParam(TENANT_QUERY_PARAM) URI tenantId) {
        verifyAuthorizedInTenantOrg(tenantId, getUserFromContext());

        int approved = 0;
        int declined = 0;
        int pending = 0;
        int failed = 0;
        Constraint constraint = AggregatedConstraint.Factory.getAggregationConstraint(ActionableEvent.class, "tenant",
                tenantId.toString(), "eventStatus");
        AggregationQueryResultList queryResults = new AggregationQueryResultList();

        _dbClient.queryByConstraint(constraint, queryResults);

        Iterator<AggregationQueryResultList.AggregatedEntry> it = queryResults.iterator();
        while (it.hasNext()) {
            AggregationQueryResultList.AggregatedEntry entry = it.next();
            if (entry.getValue().equals(ActionableEvent.Status.approved.name())) {
                approved++;
            } else if (entry.getValue().equals(ActionableEvent.Status.declined.name())) {
                declined++;
            } else if (entry.getValue().equals(ActionableEvent.Status.failed.name())) {
                failed++;
            } else {
                pending++;
            }
        }

        return new EventStatsRestRep(pending, approved, declined, failed);
    }

    @Override
    protected SearchResults getOtherSearchResults(Map<String, List<String>> parameters, boolean authorized) {
        SearchResults searchResults = new SearchResults();

        if (parameters.containsKey(RESOURCE_QUERY_PARAM)) {
            URI resourceId = URI.create(parameters.get(RESOURCE_QUERY_PARAM).get(0));

            List<ActionableEvent> events = EventUtils.findResourceEvents(_dbClient, resourceId);

            searchResults.getResource().addAll(toSearchResults(events));
        }

        return searchResults;
    }

    private List<SearchResultResourceRep> toSearchResults(List<ActionableEvent> items) {
        List<SearchResultResourceRep> results = Lists.newArrayList();

        for (ActionableEvent item : items) {
            results.add(toSearchResult(item.getId()));
        }

        return results;
    }

    private SearchResultResourceRep toSearchResult(URI uri) {
        RestLinkRep selfLink = new RestLinkRep("self", RestLinkFactory.newLink(getResourceType(), uri));
        return new SearchResultResourceRep(uri, selfLink, null);
    }

    protected ActionableEvent queryEvent(DbClient dbClient, URI id) throws DatabaseException {
        return queryObject(ActionableEvent.class, id, false);
    }

    @Override
    protected DataObject queryResource(URI id) {
        return queryObject(ActionableEvent.class, id, false);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        ActionableEvent event = queryObject(ActionableEvent.class, id, false);
        return event.getTenant();
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.EVENT;
    }
}
