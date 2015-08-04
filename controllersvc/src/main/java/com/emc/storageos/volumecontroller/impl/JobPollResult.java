/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl;

import com.emc.storageos.volumecontroller.Job;

import java.io.Serializable;

public class JobPollResult implements Serializable {

    private Job.JobStatus _status;
    // status of jog.updateStatus() execution
    private Job.JobStatus _postProcessingStatus = Job.JobStatus.SUCCESS;
    private int _percentComplete;
    private String _id;
    private String _name;
    private String _errorDescription;

    public JobPollResult() {
    }

    public String getJobName() {
        return _name;
    }

    public Job.JobStatus getJobStatus() {
        return _status;
    }

    public int getJobPercentComplete() {
        return _percentComplete;
    }

    public String getJobId() {
        return _id;
    }

    public void setJobName(String name) {
        _name = name;
    }

    public void setJobStatus(Job.JobStatus status) {
        _status = status;
    }

    public void setJobPercentComplete(int percentComplete) {
        _percentComplete = percentComplete;
    }

    public void setJobId(String id) {
        _id = id;
    }

    public String getErrorDescription() {
        return _errorDescription;
    }

    public void setErrorDescription(String _errorDescription) {
        this._errorDescription = _errorDescription;
    }

    public Job.JobStatus getJobPostProcessingStatus() {
        return _postProcessingStatus;
    }

    public void setJobPostProcessingStatus(Job.JobStatus postProcessingStatus) {
        _postProcessingStatus = postProcessingStatus;
    }

    public boolean isJobInTerminalState() {
        return (getJobStatus() == Job.JobStatus.SUCCESS || getJobStatus() == Job.JobStatus.FAILED
                || getJobStatus() == Job.JobStatus.FATAL_ERROR) &&
                (getJobPostProcessingStatus() == Job.JobStatus.SUCCESS || getJobPostProcessingStatus() == Job.JobStatus.FAILED
                || getJobPostProcessingStatus() == Job.JobStatus.FATAL_ERROR);

    }

    public boolean isJobInTerminalFailedState() {
        return (isJobInTerminalState() && !(getJobStatus() == Job.JobStatus.SUCCESS && getJobPostProcessingStatus() == Job.JobStatus.SUCCESS));
    }
}
