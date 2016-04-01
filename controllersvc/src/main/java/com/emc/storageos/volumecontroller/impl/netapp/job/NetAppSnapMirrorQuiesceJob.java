package com.emc.storageos.volumecontroller.impl.netapp.job;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.netapp.NetAppApi;
import com.emc.storageos.netapp.NetappApiFactory;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.JobPollResult;
import com.iwave.ext.netapp.model.SnapMirrorState;
import com.iwave.ext.netapp.model.SnapMirrorStatusInfo;

public class NetAppSnapMirrorQuiesceJob extends Job implements Serializable {

    private static final Logger _logger = LoggerFactory.getLogger(NetAppSnapMirrorStatusJob.class);
    private static final long ERROR_TRACKING_LIMIT = 60 * 1000; // tracking limit for transient errors. set for 2 hours

    private String _jobName;
    private URI _storageSystemUri;
    private TaskCompleter _taskCompleter;
    private List<String> _jobIds = new ArrayList<String>();

    private long _error_tracking_time = 0L;
    private JobStatus _status = JobStatus.IN_PROGRESS;

    private JobPollResult _pollResult = new JobPollResult();
    private String _errorDescription = null;

    public NetAppSnapMirrorQuiesceJob(String jobId, URI storageSystemUri, TaskCompleter taskCompleter, String jobName) {
        this._storageSystemUri = storageSystemUri;
        this._taskCompleter = taskCompleter;
        this._jobName = jobName;
        this._jobIds.add(jobId);
    }

    public NetAppSnapMirrorQuiesceJob(String jobId, URI storageSystemUri, TaskCompleter taskCompleter) {
        this._storageSystemUri = storageSystemUri;
        this._taskCompleter = taskCompleter;
        this._jobIds.add(jobId);
    }

    @Override
    public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {
        String currentJob = _jobIds.get(0);
        try {
            NetAppApi netAppApi = getNetappApi(jobContext);
            if (netAppApi == null) {
                String errorMessage = "No NetApp API found for: " + _storageSystemUri;
                processTransientError(currentJob, trackingPeriodInMillis, errorMessage, null);
            } else {
                _pollResult.setJobName(_jobName);
                _pollResult.setJobId(_taskCompleter.getOpId());

                SnapMirrorStatusInfo statusInfo = netAppApi.getSnapMirrorStateInfo(currentJob);
                if (SnapMirrorState.PAUSE.equals(statusInfo.getMirrorState())) {
                    switch (statusInfo.getTransferType()) {
                        case pending:
                        case idle:
                            _status = JobStatus.SUCCESS;
                            _pollResult.setJobPercentComplete(100);
                            _logger.info("SnapMirror Job: {} succeeded", currentJob);
                            break;
                        default:
                            break;
                    }
                } else {
                    _status = JobStatus.IN_PROGRESS;
                    _logger.info("SnapMirror Job: {} progress ", statusInfo.toString());
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
     * Get NetApp API client
     * 
     * @param jobContext
     * @return
     */
    public NetAppApi getNetappApi(JobContext jobContext) {
        StorageSystem device = jobContext.getDbClient().queryObject(StorageSystem.class, _storageSystemUri);
        NetappApiFactory factory = jobContext.getNetAppApiFactory();
        if (factory != null) {
            NetAppApi netAppApi = factory.getClient(device.getIpAddress(), device.getPortNumber(),
                    device.getUsername(), device.getPassword(), true, null);

            return netAppApi;
        }
        return null;
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

}
