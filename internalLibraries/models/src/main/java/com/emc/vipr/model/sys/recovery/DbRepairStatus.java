/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.vipr.model.sys.recovery;

import java.util.Date;
import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * This class is used by REST API to represent the db repair status of cluster
 */
@XmlRootElement(name = "db_repair_status")
public class DbRepairStatus implements Serializable {
    /**
     * The status of db repair
     */
	@XmlType(name="dbRepairStatus_Status")
    public enum Status {
        NOT_STARTED,
        IN_PROGRESS,
        SUCCESS,
        FAILED,
    }

    public DbRepairStatus() {
    }
    
    public DbRepairStatus(Status status) {
    	this.status = status;
    }

    public DbRepairStatus(Status status, Date startTime, Date endTime, int progress) {
        this.status = status;
        this.startTime = startTime;
        this.lastCompletionTime = endTime;
        this.progress = progress;
    }

    private Status status;
    private Date lastCompletionTime;
    private Date startTime;
    private int progress;

    @XmlElement(name = "status")
    public Status getStatus() {
        return this.status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @XmlElement(name = "last_completion_time")
    public Date getLastCompletionTime() {
        return this.lastCompletionTime;
    }

    public void setLastCompletionTime(Date endTime) {
        this.lastCompletionTime = endTime;
    }

    @XmlElement(name = "start_time")
    public Date getStartTime() {
        return this.startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    @XmlElement(name = "progress")
    public int getProgress() {
        return this.progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Status:");
        sb.append(getStatus());
        sb.append(", Progress:");
        sb.append(getProgress());
        sb.append(", StartTime:");
        sb.append(getStartTime());
        sb.append(", LastEndTime:");
        sb.append(getLastCompletionTime());
        return sb.toString();
    }
}
