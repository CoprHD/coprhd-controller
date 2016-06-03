/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeUtils;
import com.emc.storageos.vnxe.models.MessageOut;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXePool;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.JobPollResult;

public class VNXeJob extends Job implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeJob.class);
    private static final long ERROR_TRACKING_LIMIT = 60 * 1000; // tracking limit for transient errors. set for 2 hours
    public static int KBYTES = 1024;
    // private static final long ERROR_TRACKING_LIMIT = 2*60*60*1000;
    private JobPollResult _pollResult = new JobPollResult();
    protected List<String> _jobIds = new ArrayList<String>();
    protected URI _storageSystemUri;
    protected TaskCompleter _taskCompleter;
    protected String _jobName;
    protected String _errorDescription = null;
    protected long _error_tracking_time = 0L;
    protected JobStatus _status = JobStatus.IN_PROGRESS;
    protected boolean _isSuccess = false;
    protected Map<String, Object> _map = new HashMap<String, Object>();

    public VNXeJob(String jobId, URI storageSystemUri, TaskCompleter taskCompleter, String jobName) {
        _jobIds.add(jobId);
        _storageSystemUri = storageSystemUri;
        _taskCompleter = taskCompleter;
        _jobName = jobName;
    }

    public VNXeJob(List<String> jobIds, URI storageSystemUri, TaskCompleter taskCompleter, String jobName) {
        _jobIds = jobIds;
        _storageSystemUri = storageSystemUri;
        _taskCompleter = taskCompleter;
        _jobName = jobName;
    }

    @Override
    public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {
        String currentJob = _jobIds.get(0);
        try {
            _logger.info("VNXeJob: Looking up job: id {}", _jobIds.get(0));
            VNXeApiClient vnxeApiClient = getVNXeClient(jobContext);

            if (vnxeApiClient == null) {
                String errorMessage = "No VNXe client found for: " + _storageSystemUri;
                processTransientError(currentJob, trackingPeriodInMillis, errorMessage, null);
            } else {
                _pollResult.setJobName(_jobName);
                _pollResult.setJobId(_taskCompleter.getOpId());
                int completeCount = 0;
                boolean isSuccess = true;
                StringBuilder msg = new StringBuilder();
                for (String jobId : _jobIds) {
                    currentJob = jobId;
                    VNXeCommandJob jobResult = vnxeApiClient.getJob(jobId);
                    MessageOut msgOut = jobResult.getMessageOut();
                    int progressPct = jobResult.getProgressPct();
                    int state = jobResult.getState();
                    if (state == VNXeCommandJob.JobStatusEnum.FAILED.getValue()) {
                        completeCount++;
                        isSuccess = false;
                        msg.append("Async task failed for jobID ");
                        msg.append(jobId);
                        if (msgOut != null) {
                            msg.append(" " + msgOut.getMessage());
                        }
                        continue;
                    }
                    if (progressPct == 100 && state != VNXeCommandJob.JobStatusEnum.RUNNING.getValue()) {
                        completeCount++;
                        if (state != VNXeCommandJob.JobStatusEnum.COMPLETED.getValue()) {
                            msg.append("Async task failed for jobID ");
                            msg.append(jobId);
                            if (msgOut != null) {
                                msg.append(" " + msgOut.getMessage());
                            }
                        }
                    }
                }
                if (completeCount == _jobIds.size()) {
                    // all completed
                    _pollResult.setJobPercentComplete(100);
                    if (isSuccess) {
                        _status = JobStatus.SUCCESS;
                        _logger.info("Job: {} succeeded", _jobName);
                    } else {
                        _status = JobStatus.FAILED;
                        _errorDescription = msg.toString();
                        _logger.info(_errorDescription);

                    }
                } else {
                    _pollResult.setJobPercentComplete(100 * completeCount / _jobIds.size());
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

    public void updateStatus(JobContext jobContext) throws Exception {
        if (_status == JobStatus.SUCCESS) {
            _taskCompleter.ready(jobContext.getDbClient());
        } else if (_status == JobStatus.FAILED || _status == JobStatus.FATAL_ERROR) {
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed(_errorDescription);
            _taskCompleter.error(jobContext.getDbClient(), error);
        }
    }

    private void processTransientError(String jobId, long trackingInterval, String errorMessage, Exception ex) {
        _status = JobStatus.ERROR;
        _errorDescription = errorMessage;
        if (ex != null) {
            _logger.error(String.format("Error while processing VNXeJob - Name: %s, ID: %s, Desc: %s Status: %s",
                    _jobName, jobId, _errorDescription, _status), ex);
        } else {
            _logger.error(String.format("Error while processing VNXeJob - Name: %s, ID: %s, Desc: %s Status: %s",
                    _jobName, jobId, _errorDescription, _status));
        }

        // Check if job tracking limit was reached. Set status to FAILED in such a case.
        setErrorTrackingTime(_error_tracking_time + trackingInterval);
        _logger.info(String.format("Tracking time of VNXeJob in transient error status - %s, Name: %s, ID: %s. Status %s .",
                _error_tracking_time, _jobName, jobId, _status));
        if (_error_tracking_time > ERROR_TRACKING_LIMIT) {
            _status = JobStatus.FATAL_ERROR;
            _logger.error(String.format("Reached tracking time limit for VNXeJob - Name: %s, ID: %s. Set status to %s .",
                    _jobName, jobId, _status));
        }
    }

    public void setErrorTrackingTime(long trackingTime) {
        _error_tracking_time = trackingTime;
    }

    public void setErrorStatus(String errorDescription) {
        _status = JobStatus.FATAL_ERROR;
        _errorDescription = errorDescription;
    }

    public List<String> getJobIds() {
        return _jobIds;
    }

    public void setJobId(List<String> _jobIds) {
        this._jobIds = _jobIds;
    }

    public URI getStorageSystemUri() {
        return _storageSystemUri;
    }

    public void setStorageSystemUri(URI _storageSystemUri) {
        this._storageSystemUri = _storageSystemUri;
    }

    public TaskCompleter getTaskCompleter() {
        return _taskCompleter;
    }

    public void setTaskCompleter(TaskCompleter _taskCompleter) {
        this._taskCompleter = _taskCompleter;
    }

    public String getJobName() {
        return _jobName;
    }

    public void setJobName(String _jobName) {
        this._jobName = _jobName;
    }

    public boolean getIsSuccess() {
        return _isSuccess;
    }

    public void setIsSuccess(boolean success) {
        _isSuccess = success;
    }

    /**
     * Get VNXe API client
     * 
     * @param jobContext
     * @return
     */
    public VNXeApiClient getVNXeClient(JobContext jobContext) {

        VNXeApiClient vnxeApiClient = null;
        StorageSystem storageSystem = jobContext.getDbClient().queryObject(StorageSystem.class, _storageSystemUri);
        if (Type.unity.name().equalsIgnoreCase(storageSystem.getSystemType())) {
            vnxeApiClient = jobContext.getVNXeApiClientFactory().getUnityClient(
                    storageSystem.getIpAddress(), storageSystem.getPortNumber(),
                    storageSystem.getUsername(), storageSystem.getPassword());
        } else {
            vnxeApiClient = jobContext.getVNXeApiClientFactory().getClient(
                    storageSystem.getIpAddress(), storageSystem.getPortNumber(),
                    storageSystem.getUsername(), storageSystem.getPassword());
        }
        return vnxeApiClient;
    }

    /**
     * Update storage pool capacity
     * 
     * @param dbClient
     * @param vnxeApiClient
     * @param storagePoolUri
     * @param reservedCapacityVolumesIds The volumes reserved capacity in the pool that needs to be removed
     */
    public static void updateStoragePoolCapacity(DbClient dbClient, VNXeApiClient vnxeApiClient, URI storagePoolUri, 
            List<String> reservedCapacityVolumeIds) {
        StoragePool storagePool = dbClient.queryObject(StoragePool.class, storagePoolUri);
        if (reservedCapacityVolumeIds != null && !reservedCapacityVolumeIds.isEmpty()) {
            storagePool.removeReservedCapacityForVolumes(reservedCapacityVolumeIds);
        }
        String poolNativeId = storagePool.getNativeId();
        VNXePool pool = vnxeApiClient.getPool(poolNativeId);
        storagePool.setFreeCapacity(VNXeUtils.convertDoubleSizeToViPRLong(pool.getSizeFree()));
        storagePool.setSubscribedCapacity(VNXeUtils.convertDoubleSizeToViPRLong(pool.getSizeSubscribed()));
        dbClient.updateObject(storagePool);
    }

}
