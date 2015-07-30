/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Deprecated
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ExecutionInfo {

    /**
     * Date this order started
     */
    private Date startDate;

    /**
     * Date this order completed
     */
    private Date endDate;

    /**
     * Status of this execution
     */
    private String executionStatus;

    /**
     * The current task that is running
     */
    private String currentTask;

    /**
     * Resources that were affected (created/updated/deleted) by this service
     */
    private List<String> affectedResources;

    /**
     * Log information for this order execution
     */
    private List<ExecutionLogInfo> executionLogs;

    /**
     * Task information for this order execution
     */
    private List<ExecutionTaskInfo> executionTasks;

    @XmlElementWrapper(name = "affectedResources")
    @XmlElement(name = "resource")
    public List<String> getAffectedResources() {
        if (affectedResources == null) {
            affectedResources = new ArrayList<>();
        }
        return affectedResources;
    }

    public String getCurrentTask() {
        return currentTask;
    }

    public void setCurrentTask(String currentTask) {
        this.currentTask = currentTask;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    @XmlElementWrapper(name = "executionLogs")
    @XmlElement(name = "log")
    public List<ExecutionLogInfo> getExecutionLogs() {
        if (executionLogs == null) {
            executionLogs = new ArrayList<>();
        }
        return executionLogs;
    }

    public String getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(String executionStatus) {
        this.executionStatus = executionStatus;
    }

    @XmlElementWrapper(name = "executionTasks")
    @XmlElement(name = "task")
    public List<ExecutionTaskInfo> getExecutionTasks() {
        if (executionTasks == null) {
            executionTasks = new ArrayList<>();
        }
        return executionTasks;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }
}
