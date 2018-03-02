/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers;

import static com.emc.vipr.client.core.TasksResources.FETCH_ALL;
import static com.emc.vipr.client.core.TasksResources.SYSTEM_TENANT;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

import com.emc.sa.util.ResourceType;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.tasks.TaskStatsRestRep;
import com.emc.storageos.model.workflow.WorkflowStepRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.TaskUtil.State;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import controllers.security.Security;
import controllers.tenant.TenantSelector;
import controllers.util.Models;
import models.datatable.TaskLogsDataTable;
import models.datatable.TasksDataTable;
import play.Logger;
import play.data.binding.As;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.Util;
import play.mvc.With;
import util.MessagesUtils;
import util.TagUtils;
import util.TaskUtils;
import util.datatable.DataTablesSupport;

@With(Common.class)
public class Tasks extends Controller {
    private static final String UNKNOWN = "resource.task.unknown";
    private static final String DELETED = "resource.task.deleted";

    private static final int NORMAL_DELAY = 3000;
    private static final int MAX_TASKS = 1000;

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

        renderArgs.put("isSystemAdmin", Security.isSystemAdminOrRestrictedSystemAdmin());
        renderArgs.put("systemTasks", systemTasks);
        renderArgs.put("dataTable", new TasksDataTable(true));

        Common.angularRenderArgs().put("tenantId", systemTasks ? "system" : Models.currentAdminTenant());

        render();
    }

    public static void listAllJson(Long lastUpdated, Boolean systemTasks) {

        if (systemTasks == null) {
            systemTasks = Boolean.FALSE;
        }
        if (systemTasks && Security.isSystemAdminOrRestrictedSystemAdmin() == false) {
            forbidden();
        }

        ViPRCoreClient client = getViprClient();
        List<TaskResourceRep> taskResourceReps = null;
        if (lastUpdated == null) {
            if (systemTasks) {
                taskResourceReps = client.tasks().getByRefs(client.tasks().listByTenant(SYSTEM_TENANT, MAX_TASKS));
            }
            else {
                taskResourceReps = client.tasks().getByRefs(client.tasks().listByTenant(uri(Models.currentAdminTenant()), MAX_TASKS));
            }
        }
        else {
            taskResourceReps = taskPoll(lastUpdated, systemTasks);
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

        renderJSON(toTaskSummaries(tasks));
    }

    private static List<TaskResourceRep> tasksLongPoll(Long lastUpdated, Boolean systemTasks) {
        while (true) {
            List<TaskResourceRep> taskResourceReps = taskPoll(lastUpdated, systemTasks);
            if (!taskResourceReps.isEmpty()) {
                return taskResourceReps;
            }

            // Pause and check again
            int delay = NORMAL_DELAY;
            Logger.debug("No update for tasks, waiting for %s ms", delay);
            await(delay);
        }
    }

    private static List<TaskResourceRep> taskPoll(Long lastUpdated, Boolean systemTasks) {
        List<TaskResourceRep> taskResourceReps = Lists.newArrayList();
        ViPRCoreClient client = getViprClient();

        URI tenant = null;
        if (systemTasks) {
            tenant = SYSTEM_TENANT;
        }
        else {
            tenant = uri(Models.currentAdminTenant());
        }

        for (TaskResourceRep item : client.tasks().findCreatedSince(tenant, lastUpdated, FETCH_ALL)) {
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

        Common.angularRenderArgs().put("task", getTaskSummary(task));

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

        renderJSON(getTaskSummary(task));
    }

    @Util
    public static TaskSummary getTaskSummary(TaskResourceRep task) {
        TaskSummary taskSummary = new TaskSummary(task);

        if (task != null && task.getResource() != null && task.getResource().getId() != null) {
            ResourceType resourceType = ResourceType.fromResourceId(task.getResource().getId().toString());
            taskSummary.resourceType = resourceType.name();
        }

        taskSummary.orderId = TagUtils.getOrderIdTagValue(task);
        taskSummary.orderNumber = TagUtils.getOrderNumberTagValue(task);
        if (Security.isSystemAdmin() || Security.isSystemMonitor()) {
            if (task.getWorkflow() != null && task.getWorkflow().getId() != null) {
                taskSummary.steps = getWorkflowSteps(task.getWorkflow().getId());
            }
        }

        return taskSummary;
    }

    @Util
    public static List<WorkflowStep> getWorkflowSteps(URI workflowId) {
        List<WorkflowStepRestRep> workflowSteps = getViprClient().workflows().getSteps(workflowId);

        // Order Workflow steps by date started, not started tasks will sink to the bottom of the list
        Collections.sort(workflowSteps, new Comparator<WorkflowStepRestRep>() {
            @Override
            public int compare(WorkflowStepRestRep o1, WorkflowStepRestRep o2) {
                if (o1.getStartTime() == null && o2.getStartTime() == null) {
                    // If both steps not started yet, then just order on creation time
                    return o1.getCreationTime().compareTo(o2.getCreationTime());
                }

                if (o1.getStartTime() == null && o2.getStartTime() != null) {
                    return 1;
                }

                if (o1.getStartTime() != null && o2.getStartTime() == null) {
                    return -1;
                }

                return o1.getStartTime().compareTo(o2.getStartTime());
            }
        });

        // Get the names of all resources
        Map<String, DataObjectRestRep> systemObjects = Maps.newHashMap();
        for (WorkflowStepRestRep step : workflowSteps) {
            ResourceType type = ResourceType.fromResourceId(step.getSystem());
            DataObjectRestRep dataObject = null;
            switch (type) {
                case STORAGE_SYSTEM:
                    dataObject = getViprClient().storageSystems().get(uri(step.getSystem()));
                    break;
                case PROTECTION_SYSTEM:
                    dataObject = getViprClient().protectionSystems().get(uri(step.getSystem()));
                    break;
                case NETWORK_SYSTEM:
                    dataObject = getViprClient().networkSystems().get(uri(step.getSystem()));
                    break;
                case COMPUTE_SYSTEM:
                    dataObject = getViprClient().computeSystems().get(uri(step.getSystem()));
                    break;
            }

            if (dataObject != null) {
                systemObjects.put(step.getSystem(), dataObject);
            }
        }

        List<WorkflowStep> steps = Lists.newArrayList();
        for (WorkflowStepRestRep workflowStep : workflowSteps) {
            steps.add(new WorkflowStep(workflowStep, systemObjects));
        }

        return steps;
    }

    private static List<TaskSummary> toTaskSummaries(List<TaskResourceRep> tasks) {
        List<TaskSummary> taskSummaries = Lists.newArrayList();

        for (TaskResourceRep task : tasks) {
            TaskSummary taskSummary = new TaskSummary(task);
            taskSummary.progress = Math.max(taskSummary.progress, MINIMUM_TASK_PROGRESS);
            taskSummaries.add(taskSummary);
        }

        return taskSummaries;
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

    // "Suppressing Sonar violation of Field names should comply with naming convention"
    @SuppressWarnings("squid:S00116")
    private static class TaskSummary {
        public URI id;
        public String opId;
        public String name;
        public String description;
        public String state;
        public String message;
        public String resourceName;
        public int progress;
        public long startDate;
        public long endDate;
        public long elapsedTime;
        public String queueName;
        public long queuedStartTime;
        public long queuedElapsedTime;
        public boolean systemTask;
        public String resourceType;
        public String resourceId;
        public boolean isError = false;
        public boolean isComplete = false;
        public String serviceCode_error;
        public String serviceCode_errorDesc;
        public String serviceCode_message;
        public String orderId;
        public String orderNumber;
        public String workflowId;
        public List<WorkflowStep> steps = Collections.emptyList();
        public List<String> warningMessages = Lists.newArrayList();

        public TaskSummary(TaskResourceRep task) {
            id = task.getId();
            opId = task.getOpId();
            if (StringUtils.isBlank(task.getDescription())) {
                description = WordUtils.capitalize(task.getName().toLowerCase() + " " + task.getResource().getName());
            }
            else {
                description = task.getDescription();
            }
            message = task.getMessage();
            name = task.getName();
            state = task.getState();
            progress = task.getProgress() == null ? 0 : task.getProgress();
            startDate = task.getStartTime() == null ? 0 : task.getStartTime().getTimeInMillis();
            endDate = task.getEndTime() == null ? 0 : task.getEndTime().getTimeInMillis();
            systemTask = task.getTenant() == null;
            resourceType = task.getResource() == null ? "" : URIUtil.getTypeName(task.getResource().getId());
            resourceName = task.getResource().getName();
            resourceId = task.getResource().getId().toString();
            isComplete = !task.getState().equals("pending") && !task.getState().equals("queued");

            queuedStartTime = task.getQueuedStartTime() == null ? 0 : task.getQueuedStartTime().getTimeInMillis();

            if (NullColumnValueGetter.isNotNullValue(task.getQueueName())) {
                queueName = task.getQueueName();
            }

            if (endDate == 0) {
                elapsedTime = new Date().getTime() - startDate;
            }
            else {
                elapsedTime = endDate - startDate;
            }

            if (queuedStartTime != 0) {
                queuedElapsedTime = new Date().getTime() - queuedStartTime;
            }

            if (Security.isSecurityAdmin() || Security.isSystemMonitor()) {
                if (task.getWorkflow() != null) {
                    workflowId = task.getWorkflow().getId().toString();
                }
            }

            if (task.getServiceError() != null) {
                serviceCode_error = task.getServiceError().getCode() + "";
                serviceCode_errorDesc = task.getServiceError().getCodeDescription();
                serviceCode_message = task.getServiceError().getDetailedMessage();
            }

            // Temporary Fix since ERROR tasks don't show as complete
            if (task.getState().equals("error")) {
                progress = 100;
                isError = true;
            }

            warningMessages = task.getWarningMessages();
        }
    }

    public static class WorkflowStep {
        public String name;
        public String state;
        public String message;
        public String description;
        public String systemName;
        public long startDate;
        public long endDate;
        public long elapsedTime;
        public List<RelatedResourceRep> childFlow;
        public List<WorkflowStepRestRep> childSteps;

        public WorkflowStep(WorkflowStepRestRep step, Map<String, DataObjectRestRep> systemObjects) {
            state = step.getState();
            name = step.getName();
            message = step.getMessage();
            description = step.getDescription();

            if (step.getSystem() == null) {
                systemName = Messages.get("workflowstep.systemUnknown");
            }
            else {
                systemName = systemObjects.containsKey(step.getSystem()) ? systemObjects.get(step.getSystem()).getName() : step.getSystem();
            }
            if (step.getStartTime() != null) {
                startDate = step.getStartTime().getTime();
            }
            if (step.getEndTime() != null) {
                endDate = step.getEndTime().getTime();
            }

            if (endDate == 0) {
                elapsedTime = new Date().getTime() - startDate;
            }
            else {
                elapsedTime = endDate - startDate;
            }
            if (step.getChildWorkflows() == null) {
                childSteps = null;
            }
            else {
                childFlow = step.getChildWorkflows();
                for (int i = 0; i < childFlow.size(); i++) {
                    childSteps = getViprClient().workflows().getSteps(step.getChildWorkflows().get(i).getId());

                }
            }
        }

        public boolean isSuspended() {
            return state != null && (State.suspended_no_error.name().equalsIgnoreCase(state) ||
                    State.suspended_error.name().equalsIgnoreCase(state));
        }
    }
}
