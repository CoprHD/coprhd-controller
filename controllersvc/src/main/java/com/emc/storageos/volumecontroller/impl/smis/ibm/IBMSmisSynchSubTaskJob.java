/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.smis.ibm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.cim.CIMObjectPath;
import javax.wbem.WBEMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.impl.JobPollResult;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisSynchSubTaskJob;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

/**
 * This is to workaround a timing issue of IBM replication service when creating
 * group snapshots.
 * 
 * Upon returning from CreateGroupReplica method, TargetGroup is set to a
 * reference of IBMTSDS_SnapshotGroup, however, members of the snapshot group is
 * not available immediately somehow (the
 * IBMTSDS_GroupSynchronized.PercentSynced is 100 anyway).
 */
public class IBMSmisSynchSubTaskJob extends SmisSynchSubTaskJob {
    private static final long serialVersionUID = -2525684785231515418L;
    private static Logger _logger = LoggerFactory
            .getLogger(IBMSmisSynchSubTaskJob.class);
    private StorageSystem storageSystem;
    private String sgName = null;
    private int expectedObjCount = 0;
    private Set<String> removedCGMembers;
    private JobName jobName;
    private List<CIMObjectPath> sgMemberPaths = new ArrayList<CIMObjectPath>();
    private Set<String> cgMembers;

    public static enum JobName {
        GetNewSGMembers,
        GetRemovedCGMembers
    }

    public IBMSmisSynchSubTaskJob(CIMObjectPath cimJob,
            StorageSystem storageSystem, JobName jobName,
            String sgName, int expectedObjCount, Set<String> members) {
        super(cimJob, storageSystem.getId(), jobName.name());
        this.storageSystem = storageSystem;
        this.sgName = sgName;
        this.expectedObjCount = expectedObjCount;
        this.removedCGMembers = members;
        this.jobName = jobName;
    }

    @Override
    public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {
        switch (jobName) {
            case GetNewSGMembers:
                return pollNewSGMembers(jobContext, trackingPeriodInMillis);
            case GetRemovedCGMembers:
                return pollRemovedCGMembers(jobContext, trackingPeriodInMillis);
            default:
                return null;
        }
    }

    private JobPollResult pollNewSGMembers(JobContext jobContext, long trackingPeriodInMillis) {
        String instanceId = sgName;
        try {
            _pollResult.setJobName(getJobName());
            _pollResult.setJobId(instanceId);
            _pollResult.setJobPercentComplete(0);
            _status = JobStatus.IN_PROGRESS;

            setErrorTrackingStartTime(0L);
            sgMemberPaths = jobContext.getXIVSmisCommandHelper().getSGMembers(storageSystem, sgName);
            if (sgMemberPaths.size() >= expectedObjCount) {
                _pollResult.setJobPercentComplete(100);
                _status = JobStatus.SUCCESS;
                _logger.info("IBMSmisSynchSubTaskJob: {} succeeded", instanceId);
            }
            else {
            	// if resultObjPaths is not empty, we don't know it is full or partial result
            	// we keep trying, until time out
            }
        } catch (WBEMException e) {
            if (e.getID() == WBEMException.CIM_ERR_NOT_FOUND) {
                _status = JobStatus.FAILED;
                _errorDescription = e.getMessage();
                _logger.error(String.format(
                        "SMI-S object not found. Name: %s, ID: %s, Desc: %s",
                        getJobName(), instanceId, _errorDescription), e);
            } else {
                processTransientError(instanceId, trackingPeriodInMillis,
                        e.getMessage(), e);
            }
        } catch (Exception e) {
            processTransientError(instanceId, trackingPeriodInMillis,
                    e.getMessage(), e);
        } finally {
            try {
                _logger.info("SmisJob: Post processing job: id {}", instanceId);
                // reset from previous possible transient error in post processing status.
                _postProcessingStatus = JobStatus.SUCCESS;
                updateStatus(jobContext);
                if (_postProcessingStatus == JobStatus.ERROR) {
                    processPostProcessingError(instanceId, trackingPeriodInMillis, _errorDescription, null);
                }
            } catch (Exception e) {
                setFatalErrorStatus(e.getMessage());
                _logger.error("Problem while trying to update status", e);
            } finally {
                if (isJobInTerminalFailedState()) {
                    // Have to process job completion since updateStatus may not did this.
                    ServiceError error = DeviceControllerErrors.smis.jobFailed(_errorDescription);
                    getTaskCompleter().error(jobContext.getDbClient(), error);
                }
            }
        }

        _pollResult.setJobStatus(_status);
        _pollResult.setJobPostProcessingStatus(_postProcessingStatus);
        _pollResult.setErrorDescription(_errorDescription);
        return _pollResult;
    }

    private JobPollResult pollRemovedCGMembers(JobContext jobContext, long trackingPeriodInMillis) {
        String instanceId = null;
        try {
            CIMObjectPath objPath = getCimJob();
            instanceId = objPath.getKey(IBMSmisConstants.CP_INSTANCE_ID)
                    .toString();
            _pollResult.setJobName(getJobName());
            _pollResult.setJobId(instanceId);
            _pollResult.setJobPercentComplete(0);
            _status = JobStatus.IN_PROGRESS;

            setErrorTrackingStartTime(0L);
            cgMembers = jobContext.getXIVSmisCommandHelper().getCGMembers(storageSystem, objPath);
            if (cgMembers == null || cgMembers.isEmpty()) {
                _pollResult.setJobPercentComplete(100);
                _status = JobStatus.SUCCESS;
                _logger.info("IBMSmisSynchSubTaskJob: {} succeeded", instanceId);
            }
            else {
                Set<String> remainingMembers = Sets.intersection(removedCGMembers, cgMembers);
                if (remainingMembers.isEmpty()) {
                    _pollResult.setJobPercentComplete(100);
                    _status = JobStatus.SUCCESS;
                    _logger.info("IBMSmisSynchSubTaskJob: {} succeeded", instanceId);
                }
                else {
                    // all or some volumes are not delete
                   _logger.debug("Members: " + Joiner.on(',').join(remainingMembers) + " are still in CG");
               }
            }
        } catch (WBEMException e) {
            if (e.getID() == WBEMException.CIM_ERR_NOT_FOUND) {
                _status = JobStatus.FAILED;
                _errorDescription = e.getMessage();
                _logger.error(String.format(
                        "SMI-S object not found. Name: %s, ID: %s, Desc: %s",
                        getJobName(), instanceId, _errorDescription), e);
            } else {
                processTransientError(instanceId, trackingPeriodInMillis,
                        e.getMessage(), e);
            }
        } catch (Exception e) {
            processTransientError(instanceId, trackingPeriodInMillis,
                    e.getMessage(), e);
        } finally {
            try {
                _logger.info("SmisJob: Post processing job: id {}", instanceId);
                // reset from previous possible transient error in post processing status.
                _postProcessingStatus = JobStatus.SUCCESS;
                updateStatus(jobContext);
                if (_postProcessingStatus == JobStatus.ERROR) {
                    processPostProcessingError(instanceId, trackingPeriodInMillis, _errorDescription, null);
                }
            } catch (Exception e) {
                setFatalErrorStatus(e.getMessage());
                _logger.error("Problem while trying to update status", e);
            } finally {
                if (isJobInTerminalFailedState()) {
                    // Have to process job completion since updateStatus may not did this.
                    ServiceError error = DeviceControllerErrors.smis.jobFailed(_errorDescription);
                    getTaskCompleter().error(jobContext.getDbClient(), error);
                }
            }
        }

        _pollResult.setJobStatus(_status);
        _pollResult.setJobPostProcessingStatus(_postProcessingStatus);
        _pollResult.setErrorDescription(_errorDescription);
        return _pollResult;
    }

    public List<CIMObjectPath> getSGMemberPaths() {
        return sgMemberPaths;
    }

    public Set<String> getCGMembers() {
        return cgMembers;
    }
}
