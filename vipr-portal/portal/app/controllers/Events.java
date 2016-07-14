/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers;

import static com.emc.vipr.client.core.TasksResources.SYSTEM_TENANT;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.host.EventRestRep;
import com.emc.storageos.model.tasks.EventStatsRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;

import controllers.security.Security;
import controllers.tenant.TenantSelector;
import controllers.util.Models;
import models.datatable.EventsDataTable;
import models.datatable.TaskLogsDataTable;
import models.datatable.TasksDataTable;
import play.data.binding.As;
import play.mvc.Controller;
import play.mvc.Util;
import play.mvc.With;
import util.EventUtils;
import util.MessagesUtils;
import util.TaskUtils;
import util.datatable.DataTablesSupport;

@With(Common.class)
public class Events extends Controller {
    private static final String UNKNOWN = "resource.task.unknown";
    private static final String DELETED = "resource.task.deleted";
    private static final String APPROVED = "resource.task.approved";
    private static final String DECLINED = "resource.task.declined";

    private static final int NORMAL_DELAY = 3000;
    private static final int MAX_TASKS = 1000;

    // Currently the backend only shows progresses of 0 or 100, so for show this as the miminum progress
    private static final int MINIMUM_TASK_PROGRESS = 10;
    private static final int MILLISECONDS_IN_12HOURS = 43200000;

    private static EventsDataTable eventsDataTable = new EventsDataTable(true);

    // private static Comparator orderedTaskComparitor = new Comparator<EventRestRep>() {
    // @Override
    // public int compare(EventRestRep o1, EventRestRep o2) {
    // if (o1.getStartTime() == null || o2.getStartTime() == null) {
    // return 1;
    // }
    //
    // return o2.getStartTime().compareTo(o1.getStartTime());
    // }
    // };

    public static void listAll(Boolean systemTasks) {
        TenantSelector.addRenderArgs();

        if (systemTasks == null) {
            systemTasks = Boolean.FALSE;
        }
        if (systemTasks && Security.isSystemAdminOrRestrictedSystemAdmin() == false) {
            forbidden();
        }

        renderArgs.put("isSystemAdmin", Security.isSystemAdminOrRestrictedSystemAdmin());
        renderArgs.put("systemTasks", systemTasks);
        renderArgs.put("dataTable", new EventsDataTable(true));

        Common.angularRenderArgs().put("tenantId", systemTasks ? "system" : Models.currentAdminTenant());

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

        // Collections.sort(tasks);

        renderJSON(toEventSummaries(tasks));
    }

    public static void listAllJson(Long lastUpdated, Boolean systemTasks) {

        if (systemTasks == null) {
            systemTasks = Boolean.FALSE;
        }
        if (systemTasks && Security.isSystemAdminOrRestrictedSystemAdmin() == false) {
            forbidden();
        }

        ViPRCoreClient client = getViprClient();
        List<EventRestRep> taskResourceReps = null;
        // if (lastUpdated == null) {
        if (systemTasks) {
            taskResourceReps = client.events().getByRefs(client.events().listByTenant(SYSTEM_TENANT));
        } else {
            taskResourceReps = client.events().getByRefs(client.events().listByTenant(uri(Models.currentAdminTenant())));
        }
        // } else {
        // // taskResourceReps = taskPoll(lastUpdated, systemTasks);
        // }

        // Collections.sort(taskResourceReps, orderedTaskComparitor);

        List<EventsDataTable.Event> events = Lists.newArrayList();
        if (taskResourceReps != null) {
            for (EventRestRep eventRestRep : taskResourceReps) {
                EventsDataTable.Event event = new EventsDataTable.Event(eventRestRep);
                // if (Objects.equals(task.state, "pending") ||
                // Objects.equals(task.state, "queued")) {
                // task.progress = Math.max(task.progress, MINIMUM_TASK_PROGRESS);
                // }

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

    public static void list(String resourceId) {
        renderArgs.put("dataTable", eventsDataTable);
        render();
    }

    public static void listJson(String resourceId) {
        List<TasksDataTable.Task> tasks = null; // EventsDataTable.fetch(uri(resourceId));
        renderJSON(DataTablesSupport.createJSON(tasks, params));
    }

    public static void logsJson(String taskId) {
        List<TaskLogsDataTable.Log> logs = TaskLogsDataTable.fetch(uri(taskId));
        renderJSON(DataTablesSupport.createJSON(logs, params));
    }

    public static void itemsJson(@As(",") String[] ids) {
        List<TasksDataTable.Task> results = Lists.newArrayList();
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                if (StringUtils.isNotBlank(id)) {
                    TaskResourceRep task = TaskUtils.getTask(uri(id));
                    if (task != null) {
                        results.add(new TasksDataTable.Task(task));
                    }
                }
            }
        }
        renderJSON(results);
    }

    public static void details(String eventId) {
        if (StringUtils.isBlank(eventId)) {
            listAll(false);
        }

        EventRestRep task = EventUtils.getEvent(uri(eventId));
        if (task == null) {
            flash.error(MessagesUtils.get(UNKNOWN, eventId));
            listAll(false);
        }

        Common.angularRenderArgs().put("task", getEventSummary(task));

        render(task);
    }

    public static void detailsJson(String eventId) {
        if (StringUtils.isBlank(eventId)) {
            notFound("Task [" + eventId + "]");
        }

        EventRestRep task = EventUtils.getEvent(uri(eventId));
        if (task == null) {
            notFound("Task [" + eventId + "]");
        }

        renderJSON(getEventSummary(task));
    }

    @Util
    public static EventSummary getEventSummary(EventRestRep task) {
        EventSummary taskSummary = new EventSummary(task);
        return taskSummary;
    }

    private static List<EventSummary> toEventSummaries(List<EventRestRep> tasks) {
        List<EventSummary> taskSummaries = Lists.newArrayList();

        for (EventRestRep task : tasks) {
            EventSummary taskSummary = new EventSummary(task);
            taskSummaries.add(taskSummary);
        }

        return taskSummaries;
    }

    public static void deleteEvent(String eventId) {
        if (StringUtils.isNotBlank(eventId)) {
            getViprClient().events().deactivate(uri(eventId));
            flash.success(MessagesUtils.get(DELETED, eventId));
        }
        listAll(false);
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

        public EventSummary(EventRestRep task) {
            id = task.getId();
            message = task.getMessage();
            name = task.getName();
        }
    }
}
