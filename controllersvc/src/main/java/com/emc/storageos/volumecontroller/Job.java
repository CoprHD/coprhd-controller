/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.io.Serializable;

import com.emc.storageos.volumecontroller.impl.JobPollResult;

/**
 * A Job
 */
public abstract class Job implements Serializable {

    // A job is de-queued when it reaches a terminal condition
    public static enum JobStatus {
        IN_PROGRESS, // job is in progress
        SUCCESS,     // terminal condition
        FAILED,      // terminal condition
        ERROR,        // transient error condition (connection loss)
        FATAL_ERROR  // fatal error condition (ex. job was in error status for a long time (set to 2 hours now))
    };

    private long pollingStartTime = 0L;
    // If timeoutTimeMsec is null, QueueJobTracker will use system wide default from config properties.
    // Otherwise it can be set to a different value for specific Jobs (like VPLEX migration).
    private Long timeoutTimeMsec = null;

    /**
     * Determines job status
     * 
     * @param jobContext
     * @param trackingPeriodInMillis
     * @return
     */
    abstract public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis);

    abstract public TaskCompleter getTaskCompleter();

    public long getPollingStartTime() {
        return pollingStartTime;
    }

    public void setPollingStartTime(long pollingStartTime) {
        this.pollingStartTime = pollingStartTime;
    }

    public Long getTimeoutTimeMsec() {
        return timeoutTimeMsec;
    }

    public void setTimeoutTimeMsec(Long timeoutTimeMsec) {
        this.timeoutTimeMsec = timeoutTimeMsec;
    }
}
