/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
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
    @XmlType(name = "dbRepairStatus_Status")
    public enum Status {
        NOT_STARTED,
        IN_PROGRESS,
        SUCCESS,
        FAILED,
        UNKNOWN
    }

    private Status status;
    private Date lastCompletionTime;
    private Date startTime;
    private int progress;

    public DbRepairStatus() {
    }

    public DbRepairStatus(Status status, Date startTime, int progress) {
        this.status = status;
        this.startTime = startTime;
        this.progress = progress;
    }

    public DbRepairStatus(Status status, Date startTime, Date endTime, int progress) {
        this.status = status;
        this.startTime = startTime;
        this.lastCompletionTime = endTime;
        this.progress = progress;
    }

    /**
     * The status of db repair
     * @valid NOT_STARTED = db repair has not started yet
     * @valid IN_PROGRESS = db repair is in progress
     * @valid SUCCESS = db repair succeed
     * @valid FAILED = db repair failed
     */
    @XmlElement(name = "status")
    public Status getStatus() {
        return this.status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * The completion time of lastest successful db repair
     * @valid none
     */
    @XmlElement(name = "last_completion_time")
    public Date getLastCompletionTime() {
        return this.lastCompletionTime;
    }

    public void setLastCompletionTime(Date endTime) {
        this.lastCompletionTime = endTime;
    }

    /**
     * The start time of current db repair
     * @valid none
     */
    @XmlElement(name = "start_time")
    public Date getStartTime() {
        return this.startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    /**
     * The progress of current db repair
     * @valid 0-100, this value just for reference
     */
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
