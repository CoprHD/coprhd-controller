/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.db;

import java.util.Date;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This class is used by REST API to represent the db consistency status
 */
@XmlRootElement(name = "db_consistency_status")
@SuppressWarnings("serial")
public class DbConsistencyStatusRestRep{
    private Date startTime;
    private Date endTime;
    private Status status;
    private int progress;
    private String workingPoint;
    private int inconsistencyCount;

    /**
     * The start time of db consistency check
     */
    @XmlElement(name = "start_time")
    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    /**
     * The end time of db consistency checker
     */
    @XmlElement(name = "end_time")
    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    /**
     * The status of db consistency check
     * @valid NOT_STARTED = db consistency check has not started yet
     * @valid IN_PROGRESS = db consistency check is in progress
     * @valid SUCCESS = db consistency check succeed
     * @valid FAILED = db consistency check failed
     */
    @XmlElement(name = "status")
    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * The progress of db consistency check
     * @valid 0-100
     */
    @XmlElement(name = "progress")
    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    @XmlElement(name = "working_point")
    public String getWorkingPoint() {
        return workingPoint;
    }

    public void setWorkingPoint(String workingPoint) {
        this.workingPoint = workingPoint;
    }
    
    @XmlElement(name = "inconsistency_count")
    public int getInconsistencyCount() {
        return inconsistencyCount;
    }

    public void setInconsistencyCount(int inconsistencyCount) {
        this.inconsistencyCount = inconsistencyCount;
    }
    
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("status=").append(this.status)
          .append(",startTime=").append(this.startTime.toGMTString())
          .append(",endTime=").append(this.endTime.toGMTString())
          .append(",progress=").append(this.progress)
          .append(",workingPoint=").append(this.workingPoint);
        return sb.toString();
    }

    public enum Status {
        NOT_STARTED,
        IN_PROGRESS,
        SUCCESS,
        FAILED,
        CANCEL,
    }
}
