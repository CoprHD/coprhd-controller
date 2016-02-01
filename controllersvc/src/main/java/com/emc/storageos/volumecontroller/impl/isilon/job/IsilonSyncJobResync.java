/*
 * Copyright (c) 2015-2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.isilon.job;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.isilon.restapi.IsilonApi;
import com.emc.storageos.isilon.restapi.IsilonException;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicy.JobState;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicyReport;
import com.emc.storageos.isilon.restapi.IsilonSyncTargetPolicy;
import com.emc.storageos.isilon.restapi.IsilonSyncTargetPolicy.FOFB_STATES;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.JobPollResult;

public class IsilonSyncJobResync extends IsilonSyncJobFailover {
    private static final Logger _logger = LoggerFactory.getLogger(IsilonSyncJobResync.class);

    @Override
    public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {
        String currentJob = _jobIds.get(0);
        try {
            IsilonApi isiApiClient = getIsilonRestClient(jobContext);
            if (isiApiClient == null) {
                String errorMessage = "No Isilon REST API client found for: " + _storageSystemUri;
                processTransientError(currentJob, trackingPeriodInMillis, errorMessage, null);
            } else {
                _pollResult.setJobName(_jobName);
                _pollResult.setJobId(_taskCompleter.getOpId());

                IsilonSyncTargetPolicy targetPolicy = isiApiClient.getTargetReplicationPolicy(currentJob);
                IsilonSyncTargetPolicy.JobState policyState = targetPolicy.getLastJobState();
                if (policyState.equals(JobState.running) && targetPolicy.getFoFbState().equals(FOFB_STATES.creating_resync_policy)) {
                    _status = JobStatus.IN_PROGRESS;
                } else if (targetPolicy.getFoFbState().equals(FOFB_STATES.resync_policy_created) && policyState.equals(JobState.finished)) {
                    _status = JobStatus.SUCCESS;
                    _pollResult.setJobPercentComplete(100);
                    _logger.info("IsilonSyncJobResync: {} succeeded", currentJob);
                    String newPolicyName = currentJob;
                    newPolicyName = newPolicyName.concat("_mirror");
                    try {
                        isiApiClient.getReplicationPolicy(newPolicyName);
                    } catch (IsilonException isiex) {
                        IsilonSyncPolicyReport reportErr = isiGetReportErr(isiApiClient.getTargetReplicationPolicyReports(currentJob)
                                .getList());

                        _logger.info("Isilon reSync still need to be updated: {} succeeded", reportErr.getState().name());
                    }

                } else {
                    _errorDescription = isiGetReportErrMsg(isiApiClient.getTargetReplicationPolicyReports(currentJob).getList());
                    _pollResult.setJobPercentComplete(100);
                    _pollResult.setErrorDescription(_errorDescription);
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

        return _pollResult;
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        super.updateStatus(jobContext);
    }

    public IsilonSyncJobResync(String jobId, URI storageSystemUri, TaskCompleter taskCompleter, String jobName) {
        super(jobId, storageSystemUri, taskCompleter, jobName);
    }

}
