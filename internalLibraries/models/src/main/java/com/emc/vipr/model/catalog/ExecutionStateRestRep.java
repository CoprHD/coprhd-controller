/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "execution_state")
public class ExecutionStateRestRep {

    private Date startDate;
    private Date endDate;
    private String executionStatus;
    private String currentTask;
    private List<String> affectedResources;
    private Date lastUpdated;

    @XmlElement(name = "start_date")
    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    @XmlElement(name = "end_date")
    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    @XmlElement(name = "execution_status")
    public String getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(String executionStatus) {
        this.executionStatus = executionStatus;
    }

    @XmlElement(name = "current_task")
    public String getCurrentTask() {
        return currentTask;
    }

    public void setCurrentTask(String currentTask) {
        this.currentTask = currentTask;
    }

    @XmlElement(name = "affected_resource")
    public List<String> getAffectedResources() {
        if (this.affectedResources == null) {
            this.affectedResources = new ArrayList<>();
        }
        return affectedResources;
    }

    public void setAffectedResources(List<String> affectedResources) {
        this.affectedResources = affectedResources;
    }

    @XmlElement(name = "last_updated")
    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

}
