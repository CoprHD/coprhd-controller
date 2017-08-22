/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.system84;

import com.emc.storageos.driver.univmax.rest.type.common.JobStatus;

public class JobType {

    // min/max occurs: 1/1
    private String jobId;
    // min/max occurs: 0/1
	private String name;
    // min/max occurs: 1/1
    private JobStatus status;
    // min/max occurs: 1/1
	private String username;
    // min/max occurs: 1/1
    private String last_modified_date;
    // min/max occurs: 0/1
	private Long last_modified_date_milliseconds;
    // min/max occurs: 0/1
    private String scheduled_date;
    // min/max occurs: 0/1
	private Long scheduled_date_milliseconds;
    // min/max occurs: 0/1
    private String completed_date;
    // min/max occurs: 0/1
	private Long completed_date_milliseconds;
    // min/max occurs: 0/unbounded
    private TaskType[] task;
    // min/max occurs: 0/1
	private String resourceLink;
    // min/max occurs: 0/1
    private String result;

    public String getJobId() {
        return jobId;
    }

    public String getName() {
        return name;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getUsername() {
        return username;
    }

    public String getLast_modified_date() {
        return last_modified_date;
    }

    public Long getLast_modified_date_milliseconds() {
        return last_modified_date_milliseconds;
    }

    public String getScheduled_date() {
        return scheduled_date;
    }

    public Long getScheduled_date_milliseconds() {
        return scheduled_date_milliseconds;
    }

    public String getCompleted_date() {
        return completed_date;
    }

    public Long getCompleted_date_milliseconds() {
        return completed_date_milliseconds;
    }

    public TaskType[] getTask() {
        return task;
    }

    public String getResourceLink() {
        return resourceLink;
    }

    public String getResult() {
        return result;
    }
}
