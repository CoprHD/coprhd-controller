/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

import play.i18n.Messages;
import play.mvc.Util;

import com.emc.sa.util.ResourceType;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.workflow.WorkflowStepRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.ViPRSystemClient;
import com.emc.vipr.client.core.impl.TaskUtil.State;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.emc.vipr.client.system.LogMessageProcessor;
import com.emc.vipr.model.sys.healthmonitor.NodeHealth;
import com.emc.vipr.model.sys.logging.LogMessage;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import controllers.security.Security;

public class TaskUtils {

    private static Integer LOGS_MAX_COUNT = 10000;
    private static final List<String> LOG_NAMES = Lists.newArrayList("apisvc", "controllersvc", "syssvc", "geosvc");
    private static Integer LOG_SEVERITY = 7; // INFO
    private static final int MINIMUM_TASK_PROGRESS = 10;

    public static List<TaskResourceRep> getTasks(URI resourceId) {
        if (resourceId != null) {
            ViPRCoreClient client = getViprClient();
            return client.tasks().findByResource(resourceId);
        }
        return null;
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

    public static List<TaskSummary> toTaskSummaries(List<TaskResourceRep> tasks) {
        List<TaskSummary> taskSummaries = Lists.newArrayList();

        for (TaskResourceRep task : tasks) {
            TaskSummary taskSummary = new TaskSummary(task);
            taskSummary.progress = Math.max(taskSummary.progress, MINIMUM_TASK_PROGRESS);
            taskSummaries.add(taskSummary);
        }

        return taskSummaries;
    }
    /**
     * Returns the Details of a specified task or NULL if the task doesn't exist
     * 
     * @param taskId Id of the task
     * @return The Task details or NULL if the task doesn't exist
     */
    public static TaskResourceRep getTask(URI taskId) {
        if (taskId != null) {
            try {
                return getViprClient().tasks().get(taskId);
            } catch (ViPRHttpException e) {
                // Anything other than 404 is an error
                if (e.getHttpCode() != 404) {
                    throw e;
                }
            }
        }
        return null;
    }

    public static List<LogMessage> getTaskLogs(URI taskId) {
        TaskResourceRep task = getTask(taskId);
        return getTaskLogs(task);
    }

    public static List<LogMessage> getTaskLogs(TaskResourceRep task) {
        if (task == null) {
            return null;
        }

        ViPRSystemClient systemClient = BourneUtil.getSysClient();

        List<String> nodeIds = Lists.newArrayList();
        List<NodeHealth> nodeHealthList = systemClient.health().getHealth().getNodeHealthList();
        if (nodeHealthList != null) {
            for (NodeHealth nodeHealth : nodeHealthList) {
                if (nodeHealth != null) {
                    nodeIds.add(nodeHealth.getNodeId());
                }
            }
        }

        String start = null;
        if (task.getStartTime() != null) {
            start = Long.toString(task.getStartTime().getTimeInMillis());
        }
        String end = null;
        if (task.getEndTime() != null) {
            end = Long.toString(task.getEndTime().getTimeInMillis());
        }

        String regex = "(?i).*" + task.getOpId() + ".*";

        final List<LogMessage> logMessages = Lists.newArrayList();
        LogMessageProcessor processor = new LogMessageProcessor() {
            @Override
            public void processItem(LogMessage logMessage) throws Exception {
                logMessages.add(logMessage);
            }
        };

        systemClient.logs().getAsItems(processor, nodeIds, null, LOG_NAMES, LOG_SEVERITY, start, end, regex, LOGS_MAX_COUNT);

        return logMessages;
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

    // "Suppressing Sonar violation of Field names should comply with naming convention"
    @SuppressWarnings("squid:S00116")
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
