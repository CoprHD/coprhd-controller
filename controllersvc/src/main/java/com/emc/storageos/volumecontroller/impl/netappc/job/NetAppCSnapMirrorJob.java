/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.netappc.job;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.netappc.NetAppCApiClientFactory;
import com.emc.storageos.netappc.NetAppClusterApi;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.JobPollResult;
import com.iwave.ext.netappc.model.SnapmirrorInfoResp;
import com.iwave.ext.netappc.model.SnapmirrorRelationshipStatus;
import com.iwave.ext.netappc.model.SnapmirrorState;

public class NetAppCSnapMirrorJob extends Job implements Serializable {
    private static final Logger _logger = LoggerFactory.getLogger(NetAppCSnapMirrorJob.class);
    private static final long ERROR_TRACKING_LIMIT = 60 * 1000; // tracking limit for transient errors. set for 2 hours

    private String _jobName;
    private URI _storageSystemUri;
    private TaskCompleter _taskCompleter;
    private List<String> _jobIds = new ArrayList<String>();

    private long _error_tracking_time = 0L;
    private JobStatus _status = JobStatus.IN_PROGRESS;

    private JobPollResult _pollResult = new JobPollResult();
    private String _errorDescription = null;

    public NetAppCSnapMirrorJob(String jobId, URI storageSystemUri, TaskCompleter taskCompleter, String jobName) {
        this._storageSystemUri = storageSystemUri;
        this._taskCompleter = taskCompleter;
        this._jobName = jobName;
        this._jobIds.add(jobId);
    }

    public NetAppCSnapMirrorJob(String jobId, URI storageSystemUri, TaskCompleter taskCompleter) {
        this._storageSystemUri = storageSystemUri;
        this._taskCompleter = taskCompleter;
        this._jobName = "netAppSnapMirrorCreateJob";
        this._jobIds.add(jobId);
    }

    @Override
    public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {
        String currentJob = _jobIds.get(0);
        try {
            NetAppClusterApi netAppCApi = getNetappCApi(jobContext);
            if (netAppCApi == null) {
                String errorMessage = "No NetAppCluster API found for: " + _storageSystemUri;
                processTransientError(currentJob, trackingPeriodInMillis, errorMessage, null);
            } else {
                _pollResult.setJobName(_jobName);
                _pollResult.setJobId(_taskCompleter.getOpId());

                SnapmirrorInfoResp snapmirrorResp = netAppCApi.getSnapMirrorInfo(currentJob);
                SnapmirrorState mirrorState = SnapmirrorState.valueOfLabel(_jobName);
                SnapmirrorState currMirrorState = snapmirrorResp.getMirrorState();

                if (snapmirrorResp.getCurrentTransferError() == null) {
                    if (SnapmirrorState.READY.equals(mirrorState)) {
                        if (mirrorState.equals(currMirrorState)
                                && SnapmirrorRelationshipStatus.idle.equals(snapmirrorResp.getRelationshipStatus())) {
                            setSuccessStatus(snapmirrorResp);
                        } else {
                            setProgressStatus(snapmirrorResp);
                        }

                    } else if (SnapmirrorState.SYNCRONIZED.equals(mirrorState)) {
                        if (SnapmirrorRelationshipStatus.idle.equals(snapmirrorResp.getRelationshipStatus())) {
                            setSuccessStatus(snapmirrorResp);
                        } else {
                            setProgressStatus(snapmirrorResp);
                        }

                    } else if (SnapmirrorState.FAILOVER.equals(mirrorState) && mirrorState.equals(currMirrorState)) {
                        // set the success status
                        setSuccessStatus(snapmirrorResp);
                    } else if (SnapmirrorState.PAUSED.equals(mirrorState)) {
                        if (SnapmirrorRelationshipStatus.quiesced.equals(snapmirrorResp.getRelationshipStatus())) {

                            setSuccessStatus(snapmirrorResp);
                        } else {
                            setProgressStatus(snapmirrorResp);
                        }
                    } else {
                        setProgressStatus(snapmirrorResp);
                    }
                } else {
                    _pollResult.setErrorDescription(snapmirrorResp.getCurrentTransferError());
                    _status = JobStatus.FAILED;
                }
            }
            _pollResult.setJobStatus(_status);
            return _pollResult;
        } catch (Exception e) {
            processTransientError(_jobName, trackingPeriodInMillis, e.getMessage(), e);
        } finally {
            try {
                updateStatus(jobContext);
            } catch (Exception e) {
                setErrorStatus(e.getMessage());
                _logger.error("Problem while trying to update status", e);
            }
        }
        _pollResult.setJobStatus(_status);
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

    public void setSuccessStatus(SnapmirrorInfoResp snapmirrorResp) {
        _status = JobStatus.SUCCESS;
        _pollResult.setJobPercentComplete(100);
        _logger.info("SnapMirror Job Name: {} succeeded and details of SnapMirror Information - {}", this._jobName,
                snapmirrorResp.toString());
    }

    public void setProgressStatus(SnapmirrorInfoResp snapmirrorResp) {
        _status = JobStatus.IN_PROGRESS;
        _logger.info("SnapMirror JobName {} :  and progress details {} ", this._jobName, snapmirrorResp.toString());
    }

    public void setErrorTrackingTime(long trackingTime) {
        _error_tracking_time = trackingTime;
    }

    private void processTransientError(String jobId, long trackingInterval, String errorMessage, Exception ex) {
        _status = JobStatus.ERROR;
        _errorDescription = errorMessage;
        if (ex != null) {
            _logger.error(String.format("Error while processing SnapMirror Job - Name: %s, ID: %s, Desc: %s Status: %s",
                    _jobName, jobId, _errorDescription, _status), ex);
        } else {
            _logger.error(String.format("Error while processing SnapMirror - Name: %s, ID: %s, Desc: %s Status: %s",
                    _jobName, jobId, _errorDescription, _status));
        }

        // Check if job tracking limit was reached. Set status to FAILED in such a case.
        setErrorTrackingTime(_error_tracking_time + trackingInterval);
        _logger.info(String.format("Tracking time of SnapMirror in transient error status - %s, Name: %s, ID: %s. Status %s .",
                _error_tracking_time, _jobName, jobId, _status));
        if (_error_tracking_time > ERROR_TRACKING_LIMIT) {
            _status = JobStatus.FATAL_ERROR;
            _logger.error(String.format("Reached tracking time limit for SnapMirror - Name: %s, ID: %s. Set status to %s .",
                    _jobName, jobId, _status));
        }
    }

    /**
     * Get NetAppC Mode API client
     * 
     * @param jobContext
     * @return
     */
    public NetAppClusterApi getNetappCApi(JobContext jobContext) {
        String vserver = _jobIds.get(0);
        int i = vserver.indexOf(':');
        vserver = vserver.substring(0, i);
        StorageSystem device = jobContext.getDbClient().queryObject(StorageSystem.class, _storageSystemUri);
        NetAppCApiClientFactory factory = jobContext.getNetAppCApiClientFactory();
        if (factory != null) {
            return factory.getClient(device.getIpAddress(), device.getPortNumber(),
                    device.getUsername(), device.getPassword(), true, vserver);
        }
        return null;
    }
}
