/*
 * Copyright (c) 2012-2016 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc.model;

public class SnapmirrorJob {
    // Job state
    private SnapmirrorJobState jobState;
    // The job id.
    private Integer jobId;
    // Vserver from which the job was created
    private String jobVserver;
    // Status code. Value other than 0 indicates an error in the job execution
    private Integer jobStatusCode;
    // human-readable information about the error
    private String jobCompletion;

    // getter and setter

    public SnapmirrorJobState getJobState() {
        return jobState;
    }

    public void setJobState(SnapmirrorJobState jobState) {
        this.jobState = jobState;
    }

    public Integer getJobId() {
        return jobId;
    }

    public void setJobId(Integer jobId) {
        this.jobId = jobId;
    }

    public String getJobVserver() {
        return jobVserver;
    }

    public void setJobVserver(String jobVserver) {
        this.jobVserver = jobVserver;
    }

    public Integer getJobStatusCode() {
        return jobStatusCode;
    }

    public void setJobStatusCode(Integer jobStatusCode) {
        this.jobStatusCode = jobStatusCode;
    }

    public String getJobCompletion() {
        return jobCompletion;
    }

    public void setJobCompletion(String jobCompletion) {
        this.jobCompletion = jobCompletion;
    }

    public SnapmirrorJob() {
        // TODO Auto-generated constructor stub
    }

}
