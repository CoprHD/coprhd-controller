/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.model.valid.EnumType;
import org.apache.commons.lang.StringUtils;

import java.util.Date;

/**
 * Execution state to be persisted, for tracking progress.
 * 
 * @author jonnymiller
 */
@Cf("ExecutionState")
public class ExecutionState extends ModelObject {

    public static final String START_DATE = "startDate";
    public static final String END_DATE = "endDate";
    public static final String EXECUTION_STATUS = "executionStatus";
    public static final String CURRENT_TASK = "currentTask";
    public static final String EXECUTION_LOG_IDS = "executionLogIds";
    public static final String EXECUTION_TASK_LOG_IDS = "executionTaskLogIds";
    public static final String AFFECTED_RESOURCES = "affectedResources";
    public static final String PROXY_TOKEN = "proxyToken";

    /** The date that execution started. */
    private Date startDate;
    /** The date that execution finished. */
    private Date endDate;
    /** The current execution status. */
    private String executionStatus = ExecutionStatus.NONE.name();
    /** The current execution task (currently executing step). */
    private String currentTask;
    /** The list of resources affected by this execution. */
    /* Change to something other than String perhaps */
    private StringSet affectedResources = new StringSet();
    /** The logs for this execution. */
    private StringSet logIds = new StringSet();
    private StringSet taskLogIds = new StringSet();

    /** Proxy Authentication Token. Used to execution orders on behalf of portal user */
    private String proxyToken;

    @Name(START_DATE)
    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
        setChanged(START_DATE);
    }

    @Name(END_DATE)
    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
        setChanged(END_DATE);
    }

    @EnumType(ExecutionStatus.class)
    @Name(EXECUTION_STATUS)
    public String getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(String status) {
        this.executionStatus = status;
        setChanged(EXECUTION_STATUS);
    }

    @Name(CURRENT_TASK)
    public String getCurrentTask() {
        return currentTask;
    }

    public void setCurrentTask(String currentTask) {
        this.currentTask = currentTask;
        setChanged(CURRENT_TASK);
    }

    @Name(EXECUTION_LOG_IDS)
    public StringSet getLogIds() {
        return logIds;
    }

    public void setLogIds(StringSet logIds) {
        this.logIds = logIds;
        setChanged(EXECUTION_LOG_IDS);
    }

    @Name(EXECUTION_TASK_LOG_IDS)
    public StringSet getTaskLogIds() {
        return taskLogIds;
    }

    public void setTaskLogIds(StringSet taskLogIds) {
        this.taskLogIds = taskLogIds;
        setChanged(EXECUTION_TASK_LOG_IDS);
    }

    @Name(AFFECTED_RESOURCES)
    public StringSet getAffectedResources() {
        return affectedResources;
    }

    public void setAffectedResources(StringSet affectedResources) {
        this.affectedResources = affectedResources;
    }

    @Name(PROXY_TOKEN)
    public String getProxyToken() {
        return proxyToken;
    }

    public void setProxyToken(String proxyToken) {
        this.proxyToken = proxyToken;
        setChanged(PROXY_TOKEN);
    }

    public void addExecutionLog(ExecutionLog executionLog) {
        if (executionLog != null) {
            this.logIds.add(executionLog.getId().toString());
        }
    }

    public void removeExecutionLog(ExecutionLog executionLog) {
        if (executionLog != null) {
            this.logIds.remove(executionLog.getId().toString());
        }
    }

    public void addExecutionTaskLog(ExecutionTaskLog executionTaskLog) {
        if (executionTaskLog != null) {
            this.taskLogIds.add(executionTaskLog.getId().toString());
        }
    }

    public void removeExecuutionTaskLog(ExecutionTaskLog executionTaskLog) {
        if (executionTaskLog != null) {
            this.taskLogIds.remove(executionTaskLog.getId().toString());
        }
    }

    public void addAffectedResource(String affectedResource) {
        if (StringUtils.isNotBlank(affectedResource)) {
            this.affectedResources.add(affectedResource);
        }
    }

    public void removeAffectedResource(String affectedResource) {
        if (StringUtils.isNotBlank(affectedResource)) {
            this.affectedResources.remove(affectedResource);
        }
    }

    @Override
    public Object[] auditParameters() {
        return new Object[] { getLabel(), getId() };
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("\nExecute Status:")
                .append(getExecutionStatus())
                .append("\nStart Date:")
                .append(getStartDate())
                .append("\nEnd Date:")
                .append(getEndDate())
                .append("\nAffected Resources:")
                .append(getAffectedResources())
                .append("\n");

        return builder.toString();
    }
}
