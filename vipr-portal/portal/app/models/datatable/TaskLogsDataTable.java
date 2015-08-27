/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import util.TaskUtils;
import util.datatable.DataTable;

import com.emc.vipr.model.sys.logging.LogMessage;
import com.google.common.collect.Lists;

public class TaskLogsDataTable extends DataTable {

    public TaskLogsDataTable() {
        addColumn("timeMillis").hidden().setSearchable(false);
        addColumn("time").setRenderFunction("renderTime");
        addColumn("severity").setRenderFunction("renderSeverity");
        addColumn("message").setRenderFunction("renderMessage");
        addColumn("service");
        setDefaultSort("time_ms", "desc");
        sortAllExcept("message");
    }

    public static List<Log> fetch(URI taskId) {
        if (taskId == null) {
            return Collections.EMPTY_LIST;
        }

        List<LogMessage> logMessages = TaskUtils.getTaskLogs(taskId);

        List<Log> logs = Lists.newArrayList();
        if (logMessages != null) {
            for (LogMessage logMessage : logMessages) {
                logs.add(new Log(logMessage));
            }
        }
        return logs;
    }

    public static class Log {
        public Long timeMillis;
        public String severity;
        public String message;
        public String service;
        public String thread;
        public String node_id;
        public String node_name;
        public String line;
        public String clazz;

        public Log(LogMessage logMessage) {
            timeMillis = logMessage.getTimeMS();
            thread = logMessage.getThread();
            node_id = logMessage.getNodeId();
            node_name = logMessage.getNodeName();
            line = logMessage.getLineNumber();
            if (logMessage.getClass() != null) {
                clazz = logMessage.getClass().toString();
            }
            if (logMessage.getSeverity() != null) {
                severity = logMessage.getSeverity().toString();
            }
            message = logMessage.getMessage();
            service = logMessage.getSvcName();
        }
    }

}
