/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver;

import java.util.Calendar;

/**
 * Base abstract class for driver task. Drivers should extend this class and provide implementation for abstract methods.
 */

public abstract class DriverTask {

    private String taskId;

    public DriverTask(String taskId) {
        this.taskId = taskId;
    }

    public static enum TaskStatus {
        QUEUED,     // driver queued the request
        PROVISIONING,  // the request is provisioning
        READY,         // the request was completed
        FAILED,        // the request was failed
        PARTIALLY_FAILED,  // part of the request failed
        WARNING,          // there is a warning associated with the request
        ABORTED,          // the request was aborted
    }

    private TaskStatus status;

    private Integer progress;
    private String message;
    private Calendar startTime;
    private Calendar endTime;

    public String getTaskId() {
        return taskId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Calendar getStartTime() {
        return startTime;
    }

    public void setStartTime(Calendar startTime) {
        this.startTime = startTime;
    }

    public Calendar getEndTime() {
        return endTime;
    }

    public void setEndTime(Calendar endTime) {
        this.endTime = endTime;
    }

    /**
     * Abort request and return all resources to the original state.
     * @param task
     * @return task
     */
    public abstract DriverTask abort(DriverTask task);
}
