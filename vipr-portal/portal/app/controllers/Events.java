/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers;

import static com.emc.vipr.client.core.TasksResources.SYSTEM_TENANT;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;

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

@With(Common.class)
public class Events extends Controller {
    private static final String UNKNOWN = "resource.task.unknown";
    private static final String DELETED = "resource.task.deleted";
    private static final String APPROVED = "resource.task.approved";
    private static final String DECLINED = "resource.task.declined";

    // Currently the backend only shows progresses of 0 or 100, so for show this as the miminum progress
    private static final int MILLISECONDS_IN_12HOURS = 43200000;

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

    public static void getRecentTasks() {
        ViPRCoreClient client = getViprClient();

        long minsAgo = new Date().getTime() - MILLISECONDS_IN_12HOURS;

        List<EventRestRep> tasks = Lists.newArrayList(); // client.tasks().findCreatedSince(uri(Security.getUserInfo().getTenant()),
        // minsAgo, 5);
        // if (Security.isSecurityAdmin()) {
        // tasks.addAll(client.tasks().findCreatedSince(SYSTEM_TENANT, minsAgo, 5));
        // }

        Collections.sort(tasks, orderedEventComparator);

        renderJSON(toEventSummaries(tasks));
    }

    public static void listAllJson(Long lastUpdated) {
        ViPRCoreClient client = getViprClient();
        List<EventRestRep> taskResourceReps = null;
        // if (lastUpdated == null) {
        taskResourceReps = client.events().getByRefs(client.events().listByTenant(uri(Models.currentAdminTenant())));
        // } else {
        // // taskResourceReps = taskPoll(lastUpdated, systemTasks);
        // }

        Collections.sort(taskResourceReps, orderedEventComparator);

        List<EventsDataTable.Event> events = Lists.newArrayList();
        if (taskResourceReps != null) {
            for (EventRestRep eventRestRep : taskResourceReps) {
                EventsDataTable.Event event = new EventsDataTable.Event(eventRestRep);
                events.add(event);
            }
        }
        renderJSON(DataTablesSupport.createJSON(events, params));
    }

    public static void getActiveCount() {
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

    // private static List<TaskResourceRep> tasksLongPoll(Long lastUpdated, Boolean systemTasks) {
    // while (true) {
    // List<TaskResourceRep> taskResourceReps = taskPoll(lastUpdated, systemTasks);
    // if (!taskResourceReps.isEmpty()) {
    // return taskResourceReps;
    // }
    //
    // // Pause and check again
    // int delay = NORMAL_DELAY;
    // Logger.debug("No update for tasks, waiting for %s ms", delay);
    // await(delay);
    // }
    // }

    // private static List<EventResourceRep> taskPoll(Long lastUpdated, Boolean systemTasks) {
    // List<EventRestRep> eventReps = Lists.newArrayList();
    // ViPRCoreClient client = getViprClient();
    //
    // URI tenant = null;
    // if (systemTasks) {
    // tenant = SYSTEM_TENANT;
    // } else {
    // tenant = uri(Models.currentAdminTenant());
    // }
    //
    // for (EventRestRep item : client.events().findCreatedSince(tenant, lastUpdated, FETCH_ALL)) {
    // eventReps.add(item);
    // }
    // return taskResourceReps;
    // }

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

        render(event);
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

    public static void deleteEvent(String eventId) {
        if (StringUtils.isNotBlank(eventId)) {
            getViprClient().events().deactivate(uri(eventId));
            flash.success(MessagesUtils.get(DELETED, eventId));
        }
        listAll();
    }

    public static void approveEvent(String eventId) {
        if (StringUtils.isNotBlank(eventId)) {
            getViprClient().events().approve(uri(eventId));
            flash.success(MessagesUtils.get(APPROVED, eventId));
        }
        details(eventId);
    }

    public static void declineEvent(String eventId) {
        if (StringUtils.isNotBlank(eventId)) {
            getViprClient().events().decline(uri(eventId));
            flash.success(MessagesUtils.get(DECLINED, eventId));
        }
        details(eventId);
    }

    // "Suppressing Sonar violation of Field names should comply with naming convention"
    @SuppressWarnings("squid:S00116")
    private static class EventSummary {
        public URI id;
        public String opId;
        public String name;
        public String message;
        public long created;
        public String resourceName;
        public URI resourceId;

        public EventSummary(EventRestRep event) {
            id = event.getId();
            message = event.getDescription();
            name = event.getName();
            created = event.getCreationTime().getTimeInMillis();
            resourceName = event.getResource().getName();
            resourceId = event.getResource().getId();
        }
    }
}
