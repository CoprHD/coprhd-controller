/*
 * Copyright (c) 2015-2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.isilon.job;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.isilon.restapi.IsilonApi;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicy;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicy.JobState;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.JobPollResult;

public class IsilonSyncJobResync extends IsilonSyncIQJob {
    private static final Logger _logger = LoggerFactory.getLogger(IsilonSyncJobResync.class);
    private String policyName;

    public IsilonSyncJobResync(String policyName, URI storageSystemUri, TaskCompleter taskCompleter) {
        super(storageSystemUri, taskCompleter);
        this.policyName = policyName;
    }

    @Override
    public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {

        try {
            IsilonApi isiApiClient = getIsilonRestClient(jobContext);
            if (isiApiClient == null) {
                String errorMessage = "No Isilon REST API client found for: " + _storageSystemUri;
                processTransientError(policyName, trackingPeriodInMillis, errorMessage, null);
            } else {
                _pollResult.setJobName(_jobName);
                _pollResult.setJobId(_taskCompleter.getOpId());

                IsilonSyncPolicy policy = isiApiClient.getReplicationPolicy(policyName);
                IsilonSyncPolicy.JobState policyState = policy.getLastJobState();
                if (policyState.equals(JobState.running)) {
                    _status = JobStatus.IN_PROGRESS;
                } else if (policyState.equals(JobState.finished) && !policy.getEnabled()) {
                    _status = JobStatus.SUCCESS;
                    _pollResult.setJobPercentComplete(100);
                    _logger.info("IsilonSyncIQJob resync-prep for policy: {} succeeded", policyName);

                } else if (policyState.equals(JobState.failed)) {
                    _errorDescription = isiGetReportErrMsg(isiApiClient.getTargetReplicationPolicyReports(policyName).getList());
                    _pollResult.setJobPercentComplete(100);
                    _pollResult.setErrorDescription(_errorDescription);
                    _status = JobStatus.FAILED;
                    _logger.error("IsilonSyncIQJob: {} failed; Details: {}", policyName, _errorDescription);
                }

            }
        } catch (Exception e) {
            processTransientError(policyName, trackingPeriodInMillis, e.getMessage(), e);
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
    public void updateStatus(JobContext jobContext) throws Exception {
        super.updateStatus(jobContext);
    }

}
