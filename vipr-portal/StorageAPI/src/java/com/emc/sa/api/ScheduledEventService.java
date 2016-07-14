/*
 * Copyright (c) 2015 EMC Corporation
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
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.NamedURI;
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

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd_HH:mm:ss";

    private static final String EVENT_SERVICE_TYPE = "catalog-event";

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

    @Override
    protected ScheduledEvent queryResource(URI id) {
        return getScheduledEventById(id, false);
    }

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
        /* TODO: uncomment it back
        if (catalogService == null) {
            throw APIException.badRequests.orderServiceNotFound(
                    asString(createParam.getOrderCreateParam().getCatalogService()));
        }                                  */

        validateParam(createParam);

        ScheduledEvent newObject = null;
        try {
            newObject = createScheduledEvent(tenantId, createParam, catalogService);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return map(newObject);
    }

    private void validateParam(ScheduledEventCreateParam param) {
        ScheduleInfo scheduleInfo = param.getScheduleInfo();

        if (scheduleInfo.getHourOfDay() < 0 || scheduleInfo.getHourOfDay() > 23) {
            throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.HOUR_OF_DAY);
        }
        if (scheduleInfo.getMinuteOfHour() < 0 || scheduleInfo.getMinuteOfHour() > 59) {
            throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.MINUTE_OF_HOUR);
        }
        if (scheduleInfo.getDurationLength() < 1 || scheduleInfo.getHourOfDay() > 60*24) {
            throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.DURATION_LENGTH);
        }
        if (scheduleInfo.getCycleFrequency() < 1 ) {
            throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.CYCLE_FREQUENCE);
        }

        switch (scheduleInfo.getCycleType()) {
            case MONTHLY:
                if (scheduleInfo.getSectionsInCycle().size() != 1) {
                    throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.SECTIONS_IN_CYCLE);
                }
                int day = Integer.valueOf(scheduleInfo.getSectionsInCycle().get(0));
                if (day < 1 || day > 31) {
                    throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.SECTIONS_IN_CYCLE);
                }
                break;
            case WEEKLY:
                if (scheduleInfo.getSectionsInCycle().size() != 1) {
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
                if (scheduleInfo.getSectionsInCycle().size() != 0) {
                    throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.SECTIONS_IN_CYCLE);
                }
                break;
            default:
                throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.CYCLE_TYPE);
        }

        try {
            DateFormat formatter = new SimpleDateFormat(ScheduleInfo.FULL_DAY_FORMAT);
            Date date = formatter.parse(scheduleInfo.getStartDate());
        } catch (Exception e) {
            throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.START_DATE);
        }

        if (scheduleInfo.getReoccurrence() < 1 ) {
            throw APIException.badRequests.schduleInfoInvalid(ScheduleInfo.REOCCURRENCE);
        }

        /* TODO: exceptions for edit  */
    }

    private ScheduledEvent createScheduledEvent(URI tenantId, ScheduledEventCreateParam param, CatalogService catalogService) throws Exception{
        URI scheduledEventId = URIUtil.createId(ScheduledEvent.class);
        param.getOrderCreateParam().setScheduledEventId(scheduledEventId);
        param.getOrderCreateParam().setScheduledTime(convertCalendarToStr(getFirstScheduledTime(param.getScheduleInfo())));

        OrderRestRep restRep = orderService.createOrder(param.getOrderCreateParam());

        ScheduledEvent newObject = new ScheduledEvent();
        newObject.setId(scheduledEventId);
        newObject.setTenant(tenantId.toString());
        newObject.setCatalogServiceId(param.getOrderCreateParam().getCatalogService());
        newObject.setEventType(param.getScheduleInfo().getReoccurrence() == 1 ? ScheduledEventType.ONCE : ScheduledEventType.REOCCURRENCE);
        newObject.setEventStatus(ScheduledEventStatus.PENDING);
        newObject.setLatestOrderId(restRep.getId());

        newObject.setScheduleInfo(new String(org.apache.commons.codec.binary.Base64.encodeBase64(param.getScheduleInfo().serialize()), UTF_8));

        if (catalogService.getExecutionWindowRequired()) {
            newObject.setExecutionWindowId(catalogService.getDefaultExecutionWindowId());
        } else {
            newObject.setExecutionWindowId(new NamedURI(ExecutionWindow.INFINITE, "INFINITE"));
        }

        client.save(newObject);

        //auditOpSuccess(OperationTypeEnum.CREATE_SCHEDULED_EVENT, order.auditParameters());
        return newObject;
    }

    private String convertCalendarToStr(Calendar cal) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat(ScheduleInfo.FULL_DAYTIME_FORMAT);
        String formatted = format.format(cal.getTime());
        log.info("converted calendar time:%s", formatted);
        return formatted;
    }

    private Calendar getFirstScheduledTime(ScheduleInfo scheduleInfo) throws Exception{

        DateFormat formatter = new SimpleDateFormat(ScheduleInfo.FULL_DAY_FORMAT);
        Date date = formatter.parse(scheduleInfo.getStartDate());

        Calendar startTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        startTime.setTime(date);
        startTime.set(Calendar.HOUR_OF_DAY, scheduleInfo.getHourOfDay());
        startTime.set(Calendar.MINUTE, scheduleInfo.getMinuteOfHour());
        startTime.set(Calendar.SECOND, 0);
        log.info("startTime: %s", startTime.toString());

        Calendar currTZTime = Calendar.getInstance();
        Calendar currTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        currTime.setTimeInMillis(currTZTime.getTimeInMillis());
        log.info("currTime: %s", currTime.toString());

        Calendar initTime = startTime.before(currTime)? currTime:startTime;
        log.info("initTime: %s", initTime.toString());

        int year = initTime.get(Calendar.YEAR);
        int month = initTime.get(Calendar.MONTH);
        int day = initTime.get(Calendar.DAY_OF_MONTH);
        int hour = scheduleInfo.getHourOfDay();
        int min = scheduleInfo.getMinuteOfHour();
        switch (scheduleInfo.getCycleType()) {
            case MONTHLY:
                day = Integer.valueOf(scheduleInfo.getSectionsInCycle().get(0));
                break;
/*            case WEEKLY:
                day = Integer.valueOf(scheduleInfo.getSectionsInCycle().get(0));
                break;                                                          */
            case DAILY:
            case HOURLY:
            case MINUTELY:
                break;
            default:
                log.error("not expected schedule cycle.");
        }

        Calendar scheduledTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        scheduledTime.set(year, month, day, hour, min);
        log.info("scheduledTime: %s", scheduledTime.toString());

        while (scheduledTime.before(initTime)) {
            scheduledTime = getNextScheduledTime(scheduledTime, scheduleInfo);
            log.info("scheduledTime in loop: %s", scheduledTime.toString());
        }

        return scheduledTime;
    }

    private Calendar getNextScheduledTime(Calendar scheduledTime, ScheduleInfo scheduleInfo) {
        switch (scheduleInfo.getCycleType()) {
            case MONTHLY:
                scheduledTime.add(Calendar.MONTH, scheduleInfo.getCycleFrequency());
                break;
            case WEEKLY:
                scheduledTime.add(Calendar.WEEK_OF_MONTH, scheduleInfo.getCycleFrequency());
                break;
            case DAILY:
                scheduledTime.add(Calendar.DAY_OF_MONTH, scheduleInfo.getCycleFrequency());
                break;
            case HOURLY:
                scheduledTime.add(Calendar.HOUR_OF_DAY, scheduleInfo.getCycleFrequency());
                break;
            case MINUTELY:
                scheduledTime.add(Calendar.MINUTE, scheduleInfo.getCycleFrequency());
                break;
            default:
                log.error("not expected schedule cycle.");
        }
        return scheduledTime;
    }

    @GET
    @Path("/{id}")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public ScheduledEventRestRep getScheduledEvent(@PathParam("id") String id) {

        ScheduledEvent scheduledEvent = queryResource(uri(id));

        StorageOSUser user = getUserFromContext();
        verifyAuthorizedInTenantOrg(uri(scheduledEvent.getTenant()), user);

        return map(scheduledEvent);
    }



    private ScheduledEvent getScheduledEventById(URI id, boolean checkInactive) {
        ScheduledEvent scheduledEvent = client.scheduledEvents().findById(id);
        ArgValidator.checkEntity(scheduledEvent, id, isIdEmbeddedInURL(id), checkInactive);
        return scheduledEvent;
    }

}
