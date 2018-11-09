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
import java.util.Objects;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.tasks.TaskStatsRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;

import controllers.security.Security;
import controllers.tenant.TenantSelector;
import controllers.util.Models;
import models.datatable.TaskLogsDataTable;
import models.datatable.TasksDataTable;
import play.Logger;
import play.data.binding.As;
import play.mvc.Controller;
import play.mvc.With;
import util.MessagesUtils;
import util.StringOption;
import util.TagUtils;
import util.TaskUtils;
import util.datatable.DataTablesSupport;

@With(Common.class)
public class Tasks extends Controller {
    private static final String UNKNOWN = "resource.task.unknown";
    private static final String DELETED = "resource.task.deleted";

    private static final int NORMAL_DELAY = 3000;

    // Currently the backend only shows progresses of 0 or 100, so for show this as the miminum progress
    private static final int MINIMUM_TASK_PROGRESS = 10;
    private static final int MILLISECONDS_IN_12HOURS = 43200000;

    private static TasksDataTable tasksDataTable = new TasksDataTable(true);

    private static Comparator orderedTaskComparitor = new Comparator<TaskResourceRep>() {
        public int compare(TaskResourceRep o1, TaskResourceRep o2) {
            if (o1.getStartTime() == null || o2.getStartTime() == null) {
                return 1;
            }

            return o2.getStartTime().compareTo(o1.getStartTime());
        }
    };
    
    public static void listAll(Boolean systemTasks) {
        TenantSelector.addRenderArgs();

        if (systemTasks == null) {
            systemTasks = Boolean.FALSE;
        }

        if (systemTasks && Security.isSystemAdminOrRestrictedSystemAdmin() == false) {
            forbidden();
        }

        /**
         * To show warning message if number of tasks are more than 10K
         */
        URI tenantId = null;
        if (systemTasks) {
        	tenantId = SYSTEM_TENANT;
        } else {
        	tenantId = uri(Models.currentAdminTenant());
        }
        ViPRCoreClient client = getViprClient();
        TaskStatsRestRep stats = client.tasks().getStatsByTenant(tenantId);
        int taskCount = stats.getPending() + stats.getError() + stats.getReady();
        renderArgs.put("taskCount", taskCount);
        if (taskCount > TasksDataTable.TASK_MAX_COUNT) {
            flash.put("warning", MessagesUtils.get("tasks.warning", taskCount, TasksDataTable.TASK_MAX_COUNT));
        }

        renderArgs.put("isSystemAdmin", Security.isSystemAdminOrRestrictedSystemAdmin());
        renderArgs.put("systemTasks", systemTasks);
        renderArgs.put("dataTable", new TasksDataTable(true));

        Common.angularRenderArgs().put("tenantId", systemTasks ? "system" : Models.currentAdminTenant());
        addMaxTasksRenderArgs();
        render();
    }

    private static void addMaxTasksRenderArgs() {
        Integer maxTasks = params.get("maxTasks", Integer.class);
        if (maxTasks == null) {
            maxTasks = 100;
        }

        int[] tasks = { 100, 1000, 2000, 5000, 10000 };
        List<StringOption> options = Lists.newArrayList();
        options.add(new StringOption(String.valueOf(maxTasks), MessagesUtils.get("tasks.nTasks", maxTasks)));
        for (int taskCount : tasks) {
            if (taskCount == maxTasks) {
                options.remove(0);
            }
            options.add(new StringOption(String.valueOf(taskCount), MessagesUtils.get("tasks." + taskCount + "tasks")));
        }

        renderArgs.put("maxTasks", maxTasks);
        renderArgs.put("maxTasksOptions", options);
    }

    public static void listAllJson(Long lastUpdated, Boolean systemTasks) {

        if (systemTasks == null) {
            systemTasks = Boolean.FALSE;
        }

        if (systemTasks && Security.isSystemAdminOrRestrictedSystemAdmin() == false) {
            forbidden();
        }

        Integer maxTasks = params.get("maxTasks", Integer.class);
        if (maxTasks == null || maxTasks == 0) {
            maxTasks = 100;
        }

        ViPRCoreClient client = getViprClient();
        List<TaskResourceRep> taskResourceReps = null;
        if (lastUpdated == null) {
            if (systemTasks) {
                taskResourceReps = client.tasks().getByRefs(client.tasks().listByTenant(SYSTEM_TENANT, maxTasks));
            }
            else {
                taskResourceReps = client.tasks().getByRefs(client.tasks().listByTenant(uri(Models.currentAdminTenant()), maxTasks));
            }
        }
        else {
            taskResourceReps = taskPoll(lastUpdated, systemTasks, maxTasks);
        }

        Collections.sort(taskResourceReps, orderedTaskComparitor);

        List<TasksDataTable.Task> tasks = Lists.newArrayList();
        if (taskResourceReps != null) {
            for (TaskResourceRep taskResourceRep : taskResourceReps) {
                TasksDataTable.Task task = new TasksDataTable.Task(taskResourceRep);
                if (Objects.equals(task.state, "pending") ||
                        Objects.equals(task.state, "queued")) {
                    task.progress = Math.max(task.progress, MINIMUM_TASK_PROGRESS);
                }

                tasks.add(task);
            }
        }
        renderJSON(DataTablesSupport.createJSON(tasks, params));
    }

    public static void getActiveCount() {
        ViPRCoreClient client = getViprClient();

        int activeCount = client.tasks().getStatsByTenant(uri(Security.getUserInfo().getTenant())).getPending();
        if (Security.isSystemAdmin()) {
            activeCount += client.tasks().getStatsByTenant(SYSTEM_TENANT).getPending();
        }

        renderJSON(activeCount);
    }

    public static void getCountSummary(URI tenantId) {
        ViPRCoreClient client = getViprClient();

        TaskStatsRestRep stats = client.tasks().getStatsByTenant(tenantId);
        renderJSON(stats);
    }

    public static void getRecentTasks() {
        ViPRCoreClient client = getViprClient();

        long minsAgo = new Date().getTime() - MILLISECONDS_IN_12HOURS;

        List<TaskResourceRep> tasks = client.tasks().findCreatedSince(uri(Security.getUserInfo().getTenant()), minsAgo, 5);
        if (Security.isSecurityAdmin()) {
            tasks.addAll(client.tasks().findCreatedSince(SYSTEM_TENANT, minsAgo, 5));
        }

        Collections.sort(tasks, orderedTaskComparitor);

        renderJSON(TaskUtils.toTaskSummaries(tasks));
    }

    private static List<TaskResourceRep> tasksLongPoll(Long lastUpdated, Boolean systemTasks) {
        Integer maxTasks = params.get("maxTasks", Integer.class);
        while (true) {
            List<TaskResourceRep> taskResourceReps = taskPoll(lastUpdated, systemTasks, maxTasks);
            if (!taskResourceReps.isEmpty()) {
                return taskResourceReps;
            }

            // Pause and check again
            int delay = NORMAL_DELAY;
            Logger.debug("No update for tasks, waiting for %s ms", delay);
            await(delay);
        }
    }

    private static List<TaskResourceRep> taskPoll(Long lastUpdated, Boolean systemTasks, int maxTasks) {
        List<TaskResourceRep> taskResourceReps = Lists.newArrayList();
        ViPRCoreClient client = getViprClient();

        URI tenant = null;
        if (systemTasks) {
            tenant = SYSTEM_TENANT;
        }
        else {
            tenant = uri(Models.currentAdminTenant());
        }

        for (TaskResourceRep item : client.tasks().findCreatedSince(tenant, lastUpdated, maxTasks)) {
            taskResourceReps.add(item);
        }
        return taskResourceReps;
    }

    public static void list(String resourceId) {
        renderArgs.put("dataTable", tasksDataTable);
        render();
    }

    public static void listJson(String resourceId) {
        List<TasksDataTable.Task> tasks = TasksDataTable.fetch(uri(resourceId));
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

    public static void details(String taskId) {
        if (StringUtils.isBlank(taskId)) {
            listAll(false);
        }

        TaskResourceRep task = TaskUtils.getTask(uri(taskId));
        if (task == null) {
            flash.error(MessagesUtils.get(UNKNOWN, taskId));
            listAll(false);
        }

        if (task != null && task.getResource() != null && task.getResource().getId() != null) {
            ResourceType resourceType = ResourceType.fromResourceId(task.getResource().getId().toString());
            renderArgs.put("resourceType", resourceType);
        }

        String orderId = TagUtils.getOrderIdTagValue(task);
        String orderNumber = TagUtils.getOrderNumberTagValue(task);

        Common.angularRenderArgs().put("task", TaskUtils.getTaskSummary(task));

        TaskLogsDataTable dataTable = new TaskLogsDataTable();
        render(task, dataTable, orderId, orderNumber);
    }

    public static void detailsJson(String taskId) {
        if (StringUtils.isBlank(taskId)) {
            notFound("Task [" + taskId + "]");
        }

        TaskResourceRep task = TaskUtils.getTask(uri(taskId));
        if (task == null) {
            notFound("Task [" + taskId + "]");
        }

        renderJSON(TaskUtils.getTaskSummary(task));
    }

    public static void deleteTask(String taskId) {
        if (StringUtils.isNotBlank(taskId)) {
            getViprClient().tasks().delete(uri(taskId));
            flash.success(MessagesUtils.get(DELETED, taskId));
        }
        listAll(false);
    }

    public static void rollbackTask(String taskId) {
        if (StringUtils.isNotBlank(taskId)) {
            getViprClient().tasks().rollback(uri(taskId));
        }
        details(taskId);
    }

    public static void retryTask(String taskId) {
        if (StringUtils.isNotBlank(taskId)) {
            getViprClient().tasks().resume(uri(taskId));
        }
        details(taskId);
    }

    public static void resumeTask(String taskId) {
        if (StringUtils.isNotBlank(taskId)) {
            getViprClient().tasks().resume(uri(taskId));
        }
        details(taskId);
    }
}
   
    