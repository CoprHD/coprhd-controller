/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.TaskResourceRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.ViPRSystemClient;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.emc.vipr.client.system.LogMessageProcessor;
import com.emc.vipr.model.sys.healthmonitor.NodeHealth;
import com.emc.vipr.model.sys.logging.LogMessage;
import com.google.common.collect.Lists;

public class TaskUtils {

    private static Integer LOGS_MAX_COUNT = 10000;
    private static final List<String> LOG_NAMES = Lists.newArrayList("apisvc", "controllersvc", "syssvc", "geosvc");
    private static Integer LOG_SEVERITY = 7; // INFO

    public static List<TaskResourceRep> getTasks(URI resourceId) {
        if (resourceId != null) {
            ViPRCoreClient client = getViprClient();
            return client.tasks().findByResource(resourceId);
        }
        return null;
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
}
