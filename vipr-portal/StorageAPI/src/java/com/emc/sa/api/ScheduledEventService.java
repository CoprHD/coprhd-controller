/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api;

import static com.emc.sa.api.mapper.ScheduledEventMapper.*;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.db.client.URIUtil.asString;
import static com.emc.storageos.db.client.URIUtil.uri;

import java.net.URI;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.util.ExecutionWindowHelper;
import com.emc.sa.model.util.ScheduleTimeHelper;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import org.apache.commons.codec.binary.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.api.utils.ValidationUtils;
import com.emc.sa.catalog.CatalogServiceManager;
import com.emc.sa.descriptor.*;
import com.emc.sa.util.TextUtils;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.model.uimodels.*;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.*;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.search.SearchResults;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.vipr.client.catalog.impl.SearchConstants;
import com.emc.vipr.model.catalog.*;
import com.google.common.collect.Lists;
import com.emc.sa.api.OrderService;

@DefaultPermissions(
        readRoles = {},
        writeRoles = {})
@Path("/catalog/events")
public class ScheduledEventService extends CatalogTaggedResourceService {
    private static final Logger log = LoggerFactory.getLogger(ScheduledEventService.class);
    private static Charset UTF_8 = Charset.forName("UTF-8");
    private static final String EVENT_SERVICE_TYPE = "catalog-event";

    // Specific workaround for VMAX3 Snapshot session:
    // If a snapshot session is connected with any target, it should not be deleted for avoiding DU.
    // For scheduler, the recurrence event with retention policy could not be fulfilled.
    public String LINKED_SNAPSHOT_NAME = "linkedSnapshotName";

    @Autowired
    private ModelClient client;

    @Autowired
    private CatalogServiceManager catalogServiceManager;

    @Autowired
    private OrderService orderService;

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.SCHEDULED_EVENT;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * Query scheduled event resource via its URI.
     * @param id    scheduled event URI
     * @return      ScheduledEvent
     */
    @Override
    protected ScheduledEvent queryResource(URI id) {
        return getScheduledEventById(id, false);
    }

    /**
     * Get tenant owner of scheduled event
     * @param id    scheduled event URI
     * @return      URI of the owner tenant
     */
    @Override
    protected URI getTenantOwner(URI id) {
        ScheduledEvent event = queryResource(id);
        return uri(event.getTenant());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<ScheduledEvent> getResourceClass() {
        return ScheduledEvent.class;
    }

    /**
     * Create a scheduled event for one or a series of future orders.
     * Also a latest order is created and set to APPROVAL or SCHEDULED status
     * @param createParam   including schedule time info and order parameters
     * @return                ScheduledEventRestRep
     */
    @POST
    @Path("")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ScheduledEventRestRep createEvent(ScheduledEventCreateParam createParam) {
        StorageOSUser user = getUserFromContext();
        URI tenantId = createParam.getOrderCreateParam().getTenantId();
        if (tenantId != null) {
            verifyAuthorizedInTenantOrg(tenantId, user);
        }
        else {
            tenantId = uri(user.getTenantId());
        }

        ArgValidator.checkFieldNotNull(createParam.getOrderCreateParam().getCatalogService(), "catalogService");
        CatalogService catalogService = catalogServiceManager.getCatalogServiceById(createParam.getOrderCreateParam().getCatalogService());
        if (catalogService == null) {
            throw APIException.badRequests.orderServiceNotFound(
                    asString(createParam.getOrderCreateParam().getCatalogService()));
        }

        validateParam(createParam.getScheduleInfo());

        validOrderParam(createParam.getScheduleInfo(), createParam.getOrderCreateParam().getParameters());
        validateAutomaticExpirationNumber(createParam.getOrderCreateParam().getAdditionalScheduleInfo());

        ScheduledEvent newObject = null;
        try {
            newObject = createScheduledEvent(user, tenantId, createParam, catalogService);
        } catch (APIException ex){
            log.error(ex.getMessage(), ex);
            throw ex;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return map(newObject);
    }


    /**
     * Validate automatic expiration number has to be in range [1, 256], if user input it.
     */
    private void validateAutomaticExpirationNumber(String expiration) {
        if (expiration == null) {
            return;
        }

        try {
            int expNum = Integer.parseInt(expiration);
            if (expNum < 1 || expNum > 256) {
                throw APIException.badRequests.schduleInfoInvalid("automatic expiration number");
            }
        } catch (Exception e) {
            throw APIException.badRequests.schduleInfoInvalid("automatic expiration number");
        }
    }

    /**
     * Validate schedule time info related parameters.
     * Order related parameters would be verified later in order creation part.
     * @param scheduleInfo     Schedule Schema
     */
    private void validateParam(ScheduleInfo scheduleInfo) {
        DateFormat formatter = new SimpleDateFormat(ScheduleInfo.FULL_DAY_FORMAT);;
        Date date = null;
        try {
            date = formatter.parse(scheduleInfo.getStartDate());
        } catch (Exception e) {
            throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.START_DATE);
        }

        if (scheduleInfo.getHourOfDay() < 0 || scheduleInfo.getHourOfDay() > 23) {
            throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.HOUR_OF_DAY);
        }

        if (scheduleInfo.getMinuteOfHour() < 0 || scheduleInfo.getMinuteOfHour() > 59) {
            throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.MINUTE_OF_HOUR);
        }

        /* TODO: enable it later when we support customized duration
        if (scheduleInfo.getDurationLength() < 1 || scheduleInfo.getHourOfDay() > 60*24) {
            throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.DURATION_LENGTH);
        }
        */

        Calendar currTime, endTime;
        currTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        if (scheduleInfo.getReoccurrence() < 0) {
            throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.REOCCURRENCE);
        } else if (scheduleInfo.getReoccurrence() == 1) {
            try {
                Calendar startTime = ScheduleTimeHelper.getScheduledStartTime(scheduleInfo);
                if (startTime == null || currTime.after(startTime)) {
                    throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.START_DATE);
                }
            } catch (Exception e) {
                throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.START_DATE);
            }
            return;
        } else if (scheduleInfo.getReoccurrence() > ScheduleInfo.MAX_REOCCURRENCE ) {
            throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.REOCCURRENCE);
        }

        if (scheduleInfo.getCycleFrequency() < 1
                || scheduleInfo.getCycleFrequency() > ScheduleInfo.MAX_CYCLE_FREQUENCE ) {
            throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.CYCLE_FREQUENCE);
        }

        try {
            endTime = ScheduleTimeHelper.getScheduledEndTime(scheduleInfo);
            if (endTime != null && currTime.after(endTime)) {
                throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.END_DATE);
            }
        } catch (Exception e) {
            throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.END_DATE);
        }

        switch (scheduleInfo.getCycleType()) {
            case MONTHLY:
                if (scheduleInfo.getSectionsInCycle() == null || scheduleInfo.getSectionsInCycle().size() != 1) {
                    throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.SECTIONS_IN_CYCLE);
                }
                int day = Integer.valueOf(scheduleInfo.getSectionsInCycle().get(0));
                if (day < 1 || day > 31) {
                    throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.SECTIONS_IN_CYCLE);
                }
                break;
            case WEEKLY:
                if (scheduleInfo.getSectionsInCycle() == null || scheduleInfo.getSectionsInCycle().size() != 1) {
                    throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.SECTIONS_IN_CYCLE);
                }
                int dayOfWeek = Integer.valueOf(scheduleInfo.getSectionsInCycle().get(0));
                if (dayOfWeek < 1 || dayOfWeek > 7) {
                    throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.SECTIONS_IN_CYCLE);
                }
                break;
            case DAILY:
            case HOURLY:
            case MINUTELY:
                if (scheduleInfo.getSectionsInCycle() != null && !scheduleInfo.getSectionsInCycle().isEmpty()) {
                    throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.SECTIONS_IN_CYCLE);
                }
                break;
            default:
                throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.CYCLE_TYPE);
        }

        if (scheduleInfo.getDateExceptions() != null) {
            for (String dateException: scheduleInfo.getDateExceptions()) {
                try {
                    date = formatter.parse(dateException);
                } catch (Exception e) {
                    throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.DATE_EXCEPTIONS);
                }
            }
        }

        return;
    }

    /**
     * Check if schedule time info is matched with the desired execution window set by admin.
     * @param scheduleInfo  schedule time info
     * @param window         desired execution window set by admin
     * @return                empty for matching, otherwise including detail unmatched reason.
     */
    private String match(ScheduleInfo scheduleInfo, ExecutionWindow window) {
        String msg="";

        ExecutionWindowHelper windowHelper = new ExecutionWindowHelper(window);
        if (!windowHelper.inHourMinWindow(scheduleInfo.getHourOfDay(), scheduleInfo.getMinuteOfHour())) {
            msg = "Schedule hour/minute info does not match with execution window.";
            return msg;
        }

        if (scheduleInfo.getReoccurrence() == 1)
            return msg;

        switch (scheduleInfo.getCycleType()) {
            case MINUTELY:
            case HOURLY:
                log.warn("Not all of the orders would be scheduled due to schedule cycle type {}", scheduleInfo.getCycleType());
                break;
            case DAILY:
                if (!window.getExecutionWindowType().equals(ExecutionWindowType.DAILY.name())) {
                    msg = "Schedule cycle type has conflicts with execution window.";
                }
                break;
            case WEEKLY:
                if (window.getExecutionWindowType().equals(ExecutionWindowType.MONTHLY.name())) {
                    msg = "Schedule cycle type has conflicts with execution window.";
                } else if (window.getExecutionWindowType().equals(ExecutionWindowType.WEEKLY.name())) {
                    if (window.getDayOfWeek() != Integer.valueOf(scheduleInfo.getSectionsInCycle().get(0))) {
                        msg = "Scheduled date has conflicts with execution window.";
                    }
                }
                break;
            case MONTHLY:
                if (window.getExecutionWindowType().equals(ExecutionWindowType.WEEKLY.name())) {
                    msg = "Schedule cycle type has conflicts with execution window.";
                } else if (window.getExecutionWindowType().equals(ExecutionWindowType.MONTHLY.name())) {
                    if (window.getDayOfMonth() != Integer.valueOf(scheduleInfo.getSectionsInCycle().get(0))) {
                        msg = "Scheduled date has conflicts with execution window.";
                    }
                }
                break;
            default:
                log.error("not expected schedule cycle.");
        }

        return msg;
    }

    /**
     * Internal main function to create scheduled event.
     * @param tenantId          owner tenant Id
     * @param param             scheduled event creation param
     * @param catalogService   target catalog service
     * @return                   ScheduledEvent
     * @throws Exception
     */
    private ScheduledEvent createScheduledEvent(StorageOSUser user, URI tenantId, ScheduledEventCreateParam param, CatalogService catalogService) throws Exception{
        URI executionWindow = null;     // INFINITE execution window
        if (catalogService.getExecutionWindowRequired()) {
            if (catalogService.getDefaultExecutionWindowId() == null ||
                catalogService.getDefaultExecutionWindowId().equals(ExecutionWindow.NEXT)) {
                List<URI> executionWindows =
                        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getExecutionWindowTenantIdIdConstraint(tenantId.toString()));
                Calendar currTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                executionWindow = getNextExecutionWindow(executionWindows, currTime);
            } else {
                executionWindow = catalogService.getDefaultExecutionWindowId().getURI();
            }

            ExecutionWindow window = client.findById(executionWindow);
            String msg = match(param.getScheduleInfo(), window);
            if (!msg.isEmpty()) {
                throw APIException.badRequests.scheduleInfoNotMatchWithExecutionWindow(msg);
            }
        }

        URI scheduledEventId = URIUtil.createId(ScheduledEvent.class);
        param.getOrderCreateParam().setScheduledEventId(scheduledEventId);
        Calendar scheduledTime = ScheduleTimeHelper.getFirstScheduledTime(param.getScheduleInfo());
        param.getOrderCreateParam().setScheduledTime(ScheduleTimeHelper.convertCalendarToStr(scheduledTime));
        param.getOrderCreateParam().setExecutionWindow(executionWindow);

        OrderRestRep restRep = orderService.createOrder(param.getOrderCreateParam());

        ScheduledEvent newObject = new ScheduledEvent();
        newObject.setId(scheduledEventId);
        newObject.setTenant(tenantId.toString());
        newObject.setCatalogServiceId(param.getOrderCreateParam().getCatalogService());
        newObject.setEventType(param.getScheduleInfo().getReoccurrence() == 1 ? ScheduledEventType.ONCE : ScheduledEventType.REOCCURRENCE);
        if (catalogService.getApprovalRequired()) {
            log.info(String.format("ScheduledEventr %s requires approval", newObject.getId()));
            newObject.setEventStatus(ScheduledEventStatus.APPROVAL);
        } else {
            newObject.setEventStatus(ScheduledEventStatus.APPROVED);
        }
        newObject.setScheduleInfo(new String(org.apache.commons.codec.binary.Base64.encodeBase64(param.getScheduleInfo().serialize()), UTF_8));
        if (executionWindow != null) {
            newObject.setExecutionWindowId(new NamedURI(executionWindow, "ExecutionWindow"));
        }
        newObject.setLatestOrderId(restRep.getId());
        newObject.setOrderCreationParam(new String(org.apache.commons.codec.binary.Base64.encodeBase64(param.getOrderCreateParam().serialize()), UTF_8));
        newObject.setStorageOSUser(new String(org.apache.commons.codec.binary.Base64.encodeBase64(user.serialize()), UTF_8));

        client.save(newObject);

        log.info("Created a new scheduledEvent {}:{}", newObject.getId(),param.getScheduleInfo().toString());
        return newObject;
    }

    /**
     * Get a scheduled event via its URI
     * @param id    target schedule event URI
     * @return      ScheduledEventRestRep
     */
    @GET
    @Path("/{id}")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public ScheduledEventRestRep getScheduledEvent(@PathParam("id") String id) {
        ScheduledEvent scheduledEvent = queryResource(uri(id));

        StorageOSUser user = getUserFromContext();
        verifyAuthorizedInTenantOrg(uri(scheduledEvent.getTenant()), user);

        try {
            log.info("Fetched a scheduledEvent {}:{}", scheduledEvent.getId(),
                    ScheduleInfo.deserialize(org.apache.commons.codec.binary.Base64.decodeBase64(scheduledEvent.getScheduleInfo().getBytes(UTF_8))).toString());
        } catch (Exception e) {
            log.error("Failed to parse scheduledEvent.");
        }

        return map(scheduledEvent);
    }

    private ScheduledEvent getScheduledEventById(URI id, boolean checkInactive) {
        ScheduledEvent scheduledEvent = client.scheduledEvents().findById(id);
        ArgValidator.checkEntity(scheduledEvent, id, isIdEmbeddedInURL(id), checkInactive);
        return scheduledEvent;
    }

    /**
     * Update a scheduled event for one or a series of future orders.
     * @param updateParam   including schedule time info
     * @return                ScheduledEventRestRep
     */
    @PUT
    @Path("/{id}")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ScheduledEventRestRep updateEvent(@PathParam("id") String id, ScheduledEventUpdateParam updateParam) {
        ScheduledEvent scheduledEvent = queryResource(uri(id));
        ArgValidator.checkEntity(scheduledEvent, uri(id), true);

        validateParam(updateParam.getScheduleInfo());
        
        try {
            OrderCreateParam orderCreateParam = OrderCreateParam.deserialize(org.apache.commons.codec.binary.Base64.decodeBase64(scheduledEvent.getOrderCreationParam().getBytes(UTF_8)));
            validateAutomaticExpirationNumber(updateParam.getAdditionalScheduleInfo());
            orderCreateParam.setAdditionalScheduleInfo(updateParam.getAdditionalScheduleInfo());
            scheduledEvent.setOrderCreationParam(new String(org.apache.commons.codec.binary.Base64.encodeBase64(orderCreateParam.serialize()), UTF_8));
            updateScheduledEvent(scheduledEvent, updateParam.getScheduleInfo());
        } catch (APIException ex){
            log.error(ex.getMessage(), ex);
            throw ex;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return map(scheduledEvent);
    }

    /**
     * Internal main function to update scheduled event.
     * @param scheduledEvent   target scheduled event
     * @param scheduleInfo     target schedule schema
     * @return                   updated scheduledEvent
     * @throws Exception
     */
    private ScheduledEvent updateScheduledEvent(ScheduledEvent scheduledEvent, ScheduleInfo scheduleInfo) throws Exception{
        URI executionWindow = null;     // INFINITE execution window
        CatalogService catalogService = catalogServiceManager.getCatalogServiceById(scheduledEvent.getCatalogServiceId());
        if (catalogService.getExecutionWindowRequired()) {
            if (catalogService.getDefaultExecutionWindowId() == null ||
                catalogService.getDefaultExecutionWindowId().equals(ExecutionWindow.NEXT)) {
                List<URI> executionWindows =
                        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getExecutionWindowTenantIdIdConstraint(scheduledEvent.getTenant()));
                Calendar currTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                executionWindow = getNextExecutionWindow(executionWindows, currTime);
            } else {
                executionWindow = catalogService.getDefaultExecutionWindowId().getURI();
            }

            ExecutionWindow window = client.findById(executionWindow);
            String msg = match(scheduleInfo, window);
            if (!msg.isEmpty()) {
                throw APIException.badRequests.scheduleInfoNotMatchWithExecutionWindow(msg);
            }
        }

        Order order = client.orders().findById(scheduledEvent.getLatestOrderId());
        Calendar scheduledTime = ScheduleTimeHelper.getFirstScheduledTime(scheduleInfo);
        order.setScheduledTime(scheduledTime);
        client.save(order);

        // TODO: update execution window when admin change it in catalog service

        scheduledEvent.setScheduleInfo(new String(org.apache.commons.codec.binary.Base64.encodeBase64(scheduleInfo.serialize()), UTF_8));
        scheduledEvent.setEventType(scheduleInfo.getReoccurrence() == 1? ScheduledEventType.ONCE:ScheduledEventType.REOCCURRENCE);
        client.save(scheduledEvent);

        log.info("Updated a scheduledEvent {}:{}", scheduledEvent.getId(), scheduleInfo.toString());
        return scheduledEvent;
    }

    /**
     * Cancel a scheduled event which should be in APPROVAL or APPROVED status.
     * @param id    Scheduled Event URI
     * @return      OK if cancellation completed successfully
     */
    @POST
    @Path("/{id}/cancel")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Response cancelScheduledEvent(@PathParam("id") String id) {
        ScheduledEvent scheduledEvent = queryResource(uri(id));
        ArgValidator.checkEntity(scheduledEvent, uri(id), true);

        StorageOSUser user = getUserFromContext();
        verifyAuthorizedInTenantOrg(uri(scheduledEvent.getTenant()), user);

        if(! (scheduledEvent.getEventStatus().equals(ScheduledEventStatus.APPROVAL) ||
                scheduledEvent.getEventStatus().equals(ScheduledEventStatus.APPROVED) ||
                scheduledEvent.getEventStatus().equals(ScheduledEventStatus.REJECTED)) ) {
            throw APIException.badRequests.unexpectedValueForProperty(ScheduledEvent.EVENT_STATUS, "APPROVAL|APPROVED|REJECTED",
                    scheduledEvent.getEventStatus().name());
        }

        Order order = client.orders().findById(scheduledEvent.getLatestOrderId());
        ArgValidator.checkEntity(order, uri(id), true);
        order.setOrderStatus(OrderStatus.CANCELLED.name());
        client.save(order);

        scheduledEvent.setEventStatus(ScheduledEventStatus.CANCELLED);
        client.save(scheduledEvent);

        try {
            log.info("Cancelled a scheduledEvent {}:{}", scheduledEvent.getId(),
                    ScheduleInfo.deserialize(org.apache.commons.codec.binary.Base64.decodeBase64(scheduledEvent.getScheduleInfo().getBytes(UTF_8))).toString());
        } catch (Exception e) {
            log.error("Failed to parse scheduledEvent.");
        }
        return Response.ok().build();
    }

    /**
     * Deactivates the scheduled event and its orders
     *
     * @param id the URN of a scheduled event to be deactivated
     * @return OK if deactivation completed successfully
     * @throws DatabaseException when a DB error occurs
     */
    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public Response deactivateScheduledEvent(@PathParam("id") String id) throws DatabaseException {
        ScheduledEvent scheduledEvent = queryResource(uri(id));
        ArgValidator.checkEntity(scheduledEvent, uri(id), true);

        // deactivate all the orders from the scheduled event
        URIQueryResultList resultList = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getScheduledEventOrderConstraint(uri(id)), resultList);
        for (URI uri : resultList) {
            log.info("deleting order: {}", uri);
            Order order = _dbClient.queryObject(Order.class, uri);
            client.delete(order);
        }

        try {
            log.info("Deleting a scheduledEvent {}:{}", scheduledEvent.getId(),
                    ScheduleInfo.deserialize(org.apache.commons.codec.binary.Base64.decodeBase64(scheduledEvent.getScheduleInfo().getBytes(UTF_8))).toString());
        } catch (Exception e) {
            log.error("Failed to parse scheduledEvent.");
        }

        // deactivate the scheduled event
        client.delete(scheduledEvent);
        return Response.ok().build();
    }


    public URI getNextExecutionWindow(Collection<URI> windows, Calendar time) {
        Calendar nextWindowTime = null;
        URI nextWindow = null;
        for (URI window : windows) {
            ExecutionWindow executionWindow = _dbClient.queryObject(ExecutionWindow.class, window);
            if (executionWindow == null) continue;

            ExecutionWindowHelper helper = new ExecutionWindowHelper(executionWindow);
            Calendar windowTime = helper.calculateCurrentOrNext(time);
            if (nextWindowTime == null || nextWindowTime.after(windowTime)) {
                nextWindowTime = windowTime;
                nextWindow = window;
            }
        }

        return nextWindow;

    }

    public void validOrderParam(ScheduleInfo scheduleInfo, List<Parameter> parameters) {
        if (scheduleInfo.getReoccurrence() != 1) {
            for (Parameter param: parameters) {
                if (param.getLabel().equals(LINKED_SNAPSHOT_NAME)) {
                    if (param.getValue() != null) {
                        String snapshotName = param.getValue();
                        if (!(snapshotName.isEmpty() || snapshotName.equals("\"\""))) {
                            throw APIException.badRequests.scheduleInfoNotAllowedWithSnapshotSessionTarget();
                        }
                    }
                }
            }
        }
    }
}
