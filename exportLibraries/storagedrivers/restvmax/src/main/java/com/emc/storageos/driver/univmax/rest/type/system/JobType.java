/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.system;

import java.util.List;

import com.emc.storageos.driver.univmax.rest.type.common.GenericResultImplType;

/**
 * @author fengs5
 *
 */
public class JobType extends GenericResultImplType {
    private String jobId;
    private String name;
    private String status;
    private String username;
    private String last_modified_date;
    private long last_modified_date_milliseconds;
    private String scheduled_date;
    private long scheduled_date_milliseconds;
    private String completed_date;
    private long completed_date_milliseconds;
    private List<TaskType> task;

    /**
     * @return the jobId
     */
    public String getJobId() {
        return jobId;
    }

    /**
     * @param jobId the jobId to set
     */
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the status
     */
    public String getStatus() {
        return status;
    }

    /**
     * @param status the status to set
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param username the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return the last_modified_date
     */
    public String getLast_modified_date() {
        return last_modified_date;
    }

    /**
     * @param last_modified_date the last_modified_date to set
     */
    public void setLast_modified_date(String last_modified_date) {
        this.last_modified_date = last_modified_date;
    }

    /**
     * @return the last_modified_date_milliseconds
     */
    public long getLast_modified_date_milliseconds() {
        return last_modified_date_milliseconds;
    }

    /**
     * @param last_modified_date_milliseconds the last_modified_date_milliseconds to set
     */
    public void setLast_modified_date_milliseconds(long last_modified_date_milliseconds) {
        this.last_modified_date_milliseconds = last_modified_date_milliseconds;
    }

    /**
     * @return the scheduled_date
     */
    public String getScheduled_date() {
        return scheduled_date;
    }

    /**
     * @param scheduled_date the scheduled_date to set
     */
    public void setScheduled_date(String scheduled_date) {
        this.scheduled_date = scheduled_date;
    }

    /**
     * @return the scheduled_date_milliseconds
     */
    public long getScheduled_date_milliseconds() {
        return scheduled_date_milliseconds;
    }

    /**
     * @param scheduled_date_milliseconds the scheduled_date_milliseconds to set
     */
    public void setScheduled_date_milliseconds(long scheduled_date_milliseconds) {
        this.scheduled_date_milliseconds = scheduled_date_milliseconds;
    }

    /**
     * @return the completed_date
     */
    public String getCompleted_date() {
        return completed_date;
    }

    /**
     * @param completed_date the completed_date to set
     */
    public void setCompleted_date(String completed_date) {
        this.completed_date = completed_date;
    }

    /**
     * @return the completed_date_milliseconds
     */
    public long getCompleted_date_milliseconds() {
        return completed_date_milliseconds;
    }

    /**
     * @param completed_date_milliseconds the completed_date_milliseconds to set
     */
    public void setCompleted_date_milliseconds(long completed_date_milliseconds) {
        this.completed_date_milliseconds = completed_date_milliseconds;
    }

    /**
     * @return the task
     */
    public List<TaskType> getTask() {
        return task;
    }

    /**
     * @param task the task to set
     */
    public void setTask(List<TaskType> task) {
        this.task = task;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "JobType [jobId=" + jobId + ", name=" + name + ", status=" + status + ", username=" + username + ", last_modified_date="
                + last_modified_date + ", last_modified_date_milliseconds=" + last_modified_date_milliseconds + ", scheduled_date="
                + scheduled_date + ", scheduled_date_milliseconds=" + scheduled_date_milliseconds + ", completed_date=" + completed_date
                + ", completed_date_milliseconds=" + completed_date_milliseconds + ", task=" + task + ", getSuccess()=" + getSuccess()
                + ", getHttpCode()=" + getHttpCode() + ", getMessage()=" + getMessage() + ", isSuccessfulStatus()=" + isSuccessfulStatus()
                + "]";
    }

}
