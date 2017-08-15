/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import com.emc.storageos.volumecontroller.impl.JobPollResult;

/**
 * A Job
 */
public abstract class Job {

    // A job is de-queued when it reaches a terminal condition
    public static enum JobStatus {
        IN_PROGRESS, // job is in progress
        SUCCESS,     // terminal condition
        FAILED,      // terminal condition
        ERROR,        // transient error condition (connection loss)
        FATAL_ERROR  // fatal error condition (ex. job was in error status for a long time (set to 2 hours now))
    };

    // COP-33888: Hotfix for customer whose migration job is taking more than 24 hours.
    public static final long JOB_TRACKING_LIMIT = 24 * 7 * 60 * 60 * 1000; // tracking limit for jobs, 7 days

    private long pollingStartTime = 0L;

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
}
