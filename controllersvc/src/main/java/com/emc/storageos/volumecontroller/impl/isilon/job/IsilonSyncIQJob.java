/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.isilon.job;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.isilon.restapi.IsilonApi;
import com.emc.storageos.isilon.restapi.IsilonException;
import com.emc.storageos.isilon.restapi.IsilonSyncJob;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicy;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicyReport;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicy.JobState;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.JobPollResult;



public class IsilonSyncIQJob extends Job implements Serializable {
    private static final Logger _logger = LoggerFactory.getLogger(IsilonSyncIQJob.class);
    private static final long ERROR_TRACKING_LIMIT = 60 * 1000; // tracking limit for transient errors. set for 2 hours
    
    protected String _jobName;
    protected URI _storageSystemUri;
    protected TaskCompleter _taskCompleter;
    protected List<String> _jobIds = new ArrayList<String>();
    
    protected long _error_tracking_time = 0L;
    protected JobStatus _status = JobStatus.IN_PROGRESS;
    // status of job.updateStatus() execution
    protected JobStatus _postProcessingStatus = JobStatus.SUCCESS;
    protected Map<String, Object> _map = new HashMap<String, Object>();

    public JobPollResult _pollResult = new JobPollResult();
    public String _errorDescription = null;
    
    
    public IsilonSyncIQJob(String jobId, URI storageSystemUri, TaskCompleter taskCompleter, String jobName) {
        this._storageSystemUri = storageSystemUri;
        this._taskCompleter = taskCompleter;
        this._jobName = jobName;
        this._jobIds.add(jobId);
    }

    @Override
    public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {
        String currentJob = _jobIds.get(0);
        try {
            IsilonApi isiApiClient = getIsilonRestClient(jobContext);
            if(isiApiClient == null) {
                String errorMessage = "No Isilon REST API client found for: " + _storageSystemUri;
                processTransientError(currentJob, trackingPeriodInMillis, errorMessage, null);
            } else {
                _pollResult.setJobName(_jobName);
                _pollResult.setJobId(_taskCompleter.getOpId());
                
                IsilonSyncPolicy policy = isiApiClient.getReplicationPolicy(currentJob);
                IsilonSyncPolicy.JobState policyState = policy.getLastJobState();
                if(policyState.equals(JobState.running)) {
                    _status = JobStatus.IN_PROGRESS;
                } else if(policyState.equals(JobState.finished)){
                    _status = JobStatus.SUCCESS;
                    _pollResult.setJobPercentComplete(100);
                    _logger.info("IsilonSyncIQJob: {} succeeded", currentJob);
                    
                } else {
    
                    _errorDescription = isiGetReportErrMsg(isiApiClient .getReplicationPolicyReports(currentJob).getList());
                    _pollResult.setJobPercentComplete(100);
                    _status = JobStatus.FAILED;
                    _logger.error("IsilonSyncIQJob: {} failed; Details: {}", currentJob, _errorDescription);
                }
                
            }
        } catch (Exception e) {
            processTransientError(currentJob, trackingPeriodInMillis, e.getMessage(), e);
        } finally {
            try {
                updateStatus(jobContext);
            } catch (Exception e) {
                setErrorStatus(e.getMessage());
                _logger.error("Problem while trying to update status", e);
            }
        }
        _pollResult.setJobStatus(_status);
        _pollResult.setErrorDescription(_errorDescription);
        return _pollResult;
    }

    @Override
    public TaskCompleter getTaskCompleter() {
        return _taskCompleter;
    }
    
    public void updateStatus(JobContext jobContext) throws Exception {
        if (_status == JobStatus.SUCCESS) {
            _taskCompleter.ready(jobContext.getDbClient());
        } else if (_status == JobStatus.FAILED || _status == JobStatus.FATAL_ERROR) {
            ServiceError error = DeviceControllerErrors.isilon.jobFailed(_errorDescription);
            _taskCompleter.error(jobContext.getDbClient(), error);
        }
    }
    
    public void setErrorStatus(String errorDescription) {
        _status = JobStatus.FATAL_ERROR;
        _errorDescription = errorDescription;
    }
    
    public void setErrorTrackingTime(long trackingTime) {
        _error_tracking_time = trackingTime;
    }
    
    /**
     * Get Isilon API client
     * 
     * @param jobContext
     * @return
     */
    public IsilonApi getIsilonRestClient(JobContext jobContext) {
        StorageSystem device = jobContext.getDbClient().queryObject(StorageSystem.class, _storageSystemUri);
         if(jobContext.getIsilonApiFactory() != null) {
             IsilonApi isilonAPI;
             URI deviceURI;
             try {
                 deviceURI = new URI("https", null, device.getIpAddress(), device.getPortNumber(), "/", null, null);
             } catch (URISyntaxException ex) {
                 throw IsilonException.exceptions.errorCreatingServerURL(device.getIpAddress(), device.getPortNumber(), ex);
             }
             //get rest client
             if (device.getUsername() != null && !device.getUsername().isEmpty()) {
                 isilonAPI = jobContext.getIsilonApiFactory().getRESTClient(deviceURI, device.getUsername(), device.getPassword());
             } else {
                 isilonAPI = jobContext.getIsilonApiFactory().getRESTClient(deviceURI);
             }
             return isilonAPI;
         }
         return null;
    }
    
    private void processTransientError(String jobId, long trackingInterval, String errorMessage, Exception ex) {
        _status = JobStatus.ERROR;
        _errorDescription = errorMessage;
        if (ex != null) {
            _logger.error(String.format("Error while processing Isilon SyncIQ Job - Name: %s, ID: %s, Desc: %s Status: %s",
                    _jobName, jobId, _errorDescription, _status), ex);
        } else {
            _logger.error(String.format("Error while processing Isilon SyncIQ - Name: %s, ID: %s, Desc: %s Status: %s",
                    _jobName, jobId, _errorDescription, _status));
        }

        // Check if job tracking limit was reached. Set status to FAILED in such a case.
        setErrorTrackingTime(_error_tracking_time + trackingInterval);
        _logger.info(String.format("Tracking time of Isilon SyncIQ in transient error status - %s, Name: %s, ID: %s. Status %s .",
                _error_tracking_time, _jobName, jobId, _status));
        if (_error_tracking_time > ERROR_TRACKING_LIMIT) {
            _status = JobStatus.FATAL_ERROR;
            _logger.error(String.format("Reached tracking time limit for Isilon SyncIQ - Name: %s, ID: %s. Set status to %s .",
                    _jobName, jobId, _status));
        }
    }
    
    private String isiGetReportErrMsg(List<IsilonSyncPolicyReport> policyReports) {
        String errorMessage = "";
        for (IsilonSyncPolicyReport report : policyReports) {
            if (report.getState().equals(JobState.failed) || report.getState().equals(JobState.needs_attention)) {
                errorMessage = report.getErrors()[0];
                break;
            } else {
                continue;
            }
        }
        return errorMessage;
    }
    
   
}
