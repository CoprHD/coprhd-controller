/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package controllers;

import static com.emc.vipr.client.core.TasksResources.SYSTEM_TENANT;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static controllers.Common.flashException;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.event.EventDetailsRestRep;
import com.emc.storageos.model.event.EventRestRep;
import com.emc.storageos.model.event.EventStatsRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;

import controllers.security.Security;
import controllers.tenant.TenantSelector;
import controllers.util.Models;
import models.datatable.EventsDataTable;
import play.data.binding.As;
import play.mvc.Controller;
import play.mvc.Util;
import play.mvc.With;
import util.EventUtils;
import util.MessagesUtils;
import util.datatable.DataTablesSupport;

/**
 * The UI controller for actionable events
 *
 */
@With(Common.class)
public class Events extends Controller {
    private static final String UNKNOWN = "resources.event.unknown";
    private static final String APPROVED = "resources.event.approved";
    private static final String APPROVED_MULTIPLE = "resources.event.approved.multiple";
    private static final String DECLINED = "resources.event.declined";
    private static final String DECLINED_MULTIPLE = "resources.event.declined.multiple";
    private static final String APPROVE_CONFIRM_FAILED = "resources.events.approve.confirm.failed";
    private static final String DECLINE_CONFIRM_FAILED = "resources.events.decline.confirm.failed";
    private static final String CONFIRM_TEXT = "confirm";

    private static Comparator orderedEventComparator = new Comparator<EventRestRep>() {
        @Override
        public int compare(EventRestRep o1, EventRestRep o2) {
            if (o1.getCreationTime() == null || o2.getCreationTime() == null) {
                return 1;
            }

            return o2.getCreationTime().compareTo(o1.getCreationTime());
        }
    };

    public static void listAll() {
        TenantSelector.addRenderArgs();

        renderArgs.put("dataTable", new EventsDataTable(true));

        Common.angularRenderArgs().put("tenantId", Models.currentAdminTenant());

        render();
    }

    public static void listAllJson(Long lastUpdated) {
        ViPRCoreClient client = getViprClient();
        List<EventRestRep> eventResourceReps = client.events().getByRefs(client.events().listByTenant(uri(Models.currentAdminTenant())));

        Collections.sort(eventResourceReps, orderedEventComparator);

        List<EventsDataTable.Event> events = Lists.newArrayList();
        if (eventResourceReps != null) {
            for (EventRestRep eventRestRep : eventResourceReps) {
                EventsDataTable.Event event = new EventsDataTable.Event(eventRestRep);
                events.add(event);
            }
        }
        renderJSON(DataTablesSupport.createJSON(events, params));
    }

    public static void getPendingCount() {
        ViPRCoreClient client = getViprClient();

        int activeCount = client.events().getStatsByTenant(uri(Security.getUserInfo().getTenant())).getPending();
        if (Security.isSystemAdmin()) {
            activeCount += client.events().getStatsByTenant(SYSTEM_TENANT).getPending();
        }

        renderJSON(activeCount);
    }

    public static void getCountSummary(URI tenantId) {
        ViPRCoreClient client = getViprClient();

        EventStatsRestRep stats = client.events().getStatsByTenant(tenantId);
        renderJSON(stats);
    }

    public static void itemsJson(@As(",") String[] ids) {
        List<EventsDataTable.Event> results = Lists.newArrayList();
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                if (StringUtils.isNotBlank(id)) {
                    EventRestRep event = EventUtils.getEvent(uri(id));
                    if (event != null) {
                        results.add(new EventsDataTable.Event(event));
                    }
                }
            }
        }
        renderJSON(results);
    }

    public static void details(String eventId) {
        if (StringUtils.isBlank(eventId)) {
            listAll();
        }

        EventRestRep event = EventUtils.getEvent(uri(eventId));
        if (event == null) {
            flash.error(MessagesUtils.get(UNKNOWN, eventId));
            listAll();
        }

        Common.angularRenderArgs().put("event", getEventSummary(event));

        List<String> approveDetails = Lists.newArrayList();
        List<String> declineDetails = Lists.newArrayList();

        if (event.getEventStatus().equalsIgnoreCase(ActionableEvent.Status.pending.name().toString())) {
            EventDetailsRestRep details = getViprClient().events().getDetails(uri(eventId));
            approveDetails = details.getApproveDetails();
            declineDetails = details.getDeclineDetails();
        } else {
            approveDetails = event.getApproveDetails();
            declineDetails = event.getApproveDetails();
        }

        Common.angularRenderArgs().put("approveDetails", approveDetails);
        Common.angularRenderArgs().put("declineDetails", declineDetails);

        List<TaskResourceRep> tasks = Lists.newArrayList();
        if (event != null && event.getTaskIds() != null) {
            tasks = getViprClient().tasks().getByRefs(event.getTaskIds());
        }

        render(event, approveDetails, declineDetails, tasks);
    }

    public static void detailsJson(String eventId) {
        if (StringUtils.isBlank(eventId)) {
            notFound("Event [" + eventId + "]");
        }

        EventRestRep event = EventUtils.getEvent(uri(eventId));
        if (event == null) {
            notFound("Event [" + eventId + "]");
        }

        renderJSON(getEventSummary(event));
    }

    @Util
    public static EventSummary getEventSummary(EventRestRep event) {
        EventSummary eventSummary = new EventSummary(event);
        return eventSummary;
    }

    private static List<EventSummary> toEventSummaries(List<EventRestRep> events) {
        List<EventSummary> eventSummaries = Lists.newArrayList();

        for (EventRestRep event : events) {
            EventSummary eventSummary = new EventSummary(event);
            eventSummaries.add(eventSummary);
        }

        return eventSummaries;
    }

    public static void approveEvents(@As(",") String[] ids, String confirm) {
        try {
            if (!StringUtils.equalsIgnoreCase(confirm, CONFIRM_TEXT)) {
                throw new Exception(MessagesUtils.get(APPROVE_CONFIRM_FAILED, confirm));
            }
            for (String eventId : ids) {
                getViprClient().events().approve(uri(eventId));
            }
            flash.success(MessagesUtils.get(APPROVED_MULTIPLE));
        } catch (Exception e) {
            flashException(e);
            listAll();
        }
        listAll();
    }

    public static void declineEvents(@As(",") String[] ids, String confirm) {
        try {
            if (!StringUtils.equalsIgnoreCase(confirm, CONFIRM_TEXT)) {
                throw new Exception(MessagesUtils.get(DECLINE_CONFIRM_FAILED, confirm));
            }
            for (String eventId : ids) {
                getViprClient().events().decline(uri(eventId));
            }
            flash.success(MessagesUtils.get(DECLINED_MULTIPLE));
        } catch (Exception e) {
            flashException(e);
            listAll();
        }
        listAll();
    }

    public static void approveEvent(String eventId) {
        try {
            if (StringUtils.isNotBlank(eventId)) {
                getViprClient().events().approve(uri(eventId));
                flash.success(MessagesUtils.get(APPROVED, eventId));
            }
        } catch (Exception e) {
            flashException(e);
            details(eventId);
        }
        details(eventId);
    }

    public static void declineEvent(String eventId) {
        try {
            if (StringUtils.isNotBlank(eventId)) {
                getViprClient().events().decline(uri(eventId));
                flash.success(MessagesUtils.get(DECLINED, eventId));
            }
        } catch (Exception e) {
            flashException(e);
            details(eventId);
        }
        details(eventId);
    }

    public static void itemDetails(String id) {
        EventRestRep event = getViprClient().events().get(uri(id));
        List<String> approveDetails = Lists.newArrayList();
        List<String> declineDetails = Lists.newArrayList();

        if (event.getEventStatus().equalsIgnoreCase(ActionableEvent.Status.pending.name().toString())) {
            EventDetailsRestRep details = getViprClient().events().getDetails(uri(id));
            approveDetails = details.getApproveDetails();
            declineDetails = details.getDeclineDetails();
        } else {
            approveDetails = event.getApproveDetails();
            declineDetails = event.getApproveDetails();
        }

        List<TaskResourceRep> tasks = Lists.newArrayList();
        if (event != null && event.getTaskIds() != null) {
            tasks = getViprClient().tasks().getByRefs(event.getTaskIds());
        }

        render(approveDetails, declineDetails, event, tasks);
    }

    // "Suppressing Sonar violation of Field names should comply with naming convention"
    @SuppressWarnings("squid:S00116")
    private static class EventSummary {
        public URI id;
        public String opId;
        public String name;
        public String description;
        public long created;
        public String resourceName;
        public URI resourceId;
        public String eventStatus;
        public String eventCode;
        public String warning;
        public long eventExecutionTime;
        public List<String> approveDetails;
        public List<String> declineDetails;

        public EventSummary(EventRestRep event) {
            id = event.getId();
            description = event.getDescription();
            name = event.getName();
            created = event.getCreationTime().getTimeInMillis();
            resourceName = event.getResource().getName();
            resourceId = event.getResource().getId();
            eventStatus = event.getEventStatus();
            eventCode = event.getEventCode();
            warning = event.getWarning();
            eventExecutionTime = event.getEventExecutionTime().getTimeInMillis();
            approveDetails = event.getApproveDetails();
            declineDetails = event.getDeclineDetails();
        }
    }
}
