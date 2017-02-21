/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.isilon;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.isilon.restapi.IsilonApi;
import com.emc.storageos.isilon.restapi.IsilonApiFactory;
import com.emc.storageos.isilon.restapi.IsilonException;
import com.emc.storageos.isilon.restapi.IsilonSyncJob;
import com.emc.storageos.isilon.restapi.IsilonSyncJob.Action;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicy;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicy.JobState;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicyReport;
import com.emc.storageos.isilon.restapi.IsilonSyncTargetPolicy;
import com.emc.storageos.isilon.restapi.IsilonSyncTargetPolicy.FOFB_STATES;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.file.MirrorFileRefreshTaskCompleter;
import com.emc.storageos.volumecontroller.impl.isilon.job.IsilonSyncIQJob;
import com.emc.storageos.volumecontroller.impl.isilon.job.IsilonSyncJobFailover;
import com.emc.storageos.volumecontroller.impl.isilon.job.IsilonSyncJobResync;
import com.emc.storageos.volumecontroller.impl.isilon.job.IsilonSyncJobStart;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class IsilonMirrorOperations {
    private static final Logger _log = LoggerFactory.getLogger(IsilonMirrorOperations.class);
    private DbClient _dbClient;
    private IsilonApiFactory _factory;

    public IsilonApiFactory getIsilonApiFactory() {
        return _factory;
    }

    /**
     * Set Isilon API factory
     * 
     * @param factory
     */
    public void setIsilonApiFactory(IsilonApiFactory factory) {
        _factory = factory;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void deleteMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {
        IsilonSyncPolicy policy;
        IsilonApi isi = null;
        BiosCommandResult cmdResult = null;
        FileShare targetFileShare = _dbClient.queryObject(FileShare.class, target);
        FileShare sourceFileShare = _dbClient.queryObject(FileShare.class, source);
        String policyName = targetFileShare.getLabel();
        StorageSystem sourceStorageSystem = _dbClient.queryObject(StorageSystem.class, sourceFileShare.getStorageDevice());
        isi = getIsilonDevice(sourceStorageSystem);

        try {
            policy = isi.getReplicationPolicy(policyName);
        } catch (IsilonException e) {
            _log.info("Not able to get policy : {} due to : {} ", policyName, e.getMessage());
            completer.ready(_dbClient);
            WorkflowStepCompleter.stepSucceded(completer.getOpId());
            return;
        }
        if (policy != null) {
            cmdResult = dodeleteReplicationPolicy(system, policyName);
        }

        // Check if mirror policy exists on target system if yes, delete it..
        if (cmdResult != null && cmdResult.getCommandSuccess()) {
            StorageSystem targetStorageSystem = _dbClient.queryObject(StorageSystem.class, targetFileShare.getStorageDevice());
            isi = getIsilonDevice(targetStorageSystem);
            String mirrorPolicyName = policyName.concat("_mirror");
            try {
                policy = isi.getReplicationPolicy(mirrorPolicyName);
            } catch (IsilonException e) {
                _log.info("Mirror policy named : {} not found on the target system", mirrorPolicyName);
                completer.ready(_dbClient);
                WorkflowStepCompleter.stepSucceded(completer.getOpId());
                return;
            }
            if (policy != null) {
                cmdResult = dodeleteReplicationPolicy(targetStorageSystem, mirrorPolicyName);
            }
        }

        if (cmdResult != null && cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
            WorkflowStepCompleter.stepSucceded(completer.getOpId());
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }
    }

    /**
     * Enable the Isilon syncIQ policy
     * 
     * @param isi
     * @param policyName
     * @return
     */
    IsilonSyncPolicy doEnableReplicationPolicy(IsilonApi isi, String policyName) {
        IsilonSyncPolicy modifiedPolicy = new IsilonSyncPolicy();
        modifiedPolicy.setName(policyName);
        modifiedPolicy.setEnabled(true);

        isi.modifyReplicationPolicy(policyName, modifiedPolicy);
        return isi.getReplicationPolicy(policyName);
    }

    /**
     * Call to isilon to start replication session
     * 
     * @param system
     * @param policyName
     * @return
     */
    public BiosCommandResult doStartReplicationPolicy(StorageSystem system, String policyName,
            TaskCompleter taskCompleter) {
        _log.info("IsilonMirrorOperations -  doStartReplicationPolicy started on storagesystem {}", system.getLabel());
        try {

            IsilonApi isi = getIsilonDevice(system);
            IsilonSyncPolicy policy = isi.getReplicationPolicy(policyName);
            IsilonSyncPolicy.JobState policyState = policy.getLastJobState();

            if (!policy.getEnabled()) {
                policy = doEnableReplicationPolicy(isi, policyName);
                if (policy.getEnabled()) {
                    _log.info("Replication Policy - {} ENABLED successfully", policy.toString());
                }
            }
            if (!policyState.equals(JobState.running) || !policyState.equals(JobState.paused)
                    || !policyState.equals(JobState.resumed)) {
                IsilonSyncJob job = new IsilonSyncJob();
                job.setId(policyName);
                isi.modifyReplicationJob(job);
                policy = isi.getReplicationPolicy(policyName);

                IsilonSyncJobStart isiSyncJobStart = new IsilonSyncJobStart(policyName, system.getId(), taskCompleter, policyName);
                try {
                    ControllerServiceImpl.enqueueJob(new QueueJob(isiSyncJobStart));
                    return BiosCommandResult.createPendingResult();
                } catch (Exception ex) {
                    _log.error("Start Replication Job Failed ", ex);
                    ServiceError error = DeviceControllerErrors.isilon.jobFailed("Start Replication Job Failed as:" + ex.getMessage());
                    if (taskCompleter != null) {
                        taskCompleter.error(_dbClient, error);
                    }
                    return BiosCommandResult.createErrorResult(error);
                }
            } else {
                _log.error("Replication Policy - {} can't be STARTED because policy is in {} state", policyName,
                        policyState);
                ServiceError error = DeviceControllerErrors.isilon
                        .jobFailed("doStartReplicationPolicy as : Replication Policy can't be STARTED because "
                                + "policy is already in Active state");
                return BiosCommandResult.createErrorResult(error);
            }
        } catch (IsilonException e) {
            _log.error("doStartReplicationPolicy failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    /**
     * Call to device to cancel policy
     * 
     * @param system
     * @param policyName
     * @return
     */
    public BiosCommandResult doCancelReplicationPolicy(StorageSystem system, String policyName, TaskCompleter taskCompleter) {
        IsilonSyncPolicy policy = null;
        try {
            IsilonApi isi = getIsilonDevice(system);
            try {
                policy = isi.getReplicationPolicy(policyName);
            } catch (IsilonException e) {
                policy = null;
                return BiosCommandResult.createSuccessfulResult();
            }

            if (policy != null) {
                JobState policyState = policy.getLastJobState();

                if (policyState.equals(JobState.running) || policyState.equals(JobState.paused)) {
                    _log.info("Canceling Replication Policy  -{} because policy is in - {} state ", policyName, policyState);
                    IsilonSyncPolicy modifiedPolicy = new IsilonSyncPolicy();
                    modifiedPolicy.setName(policyName);
                    modifiedPolicy.setLastJobState(JobState.canceled);
                    isi.modifyReplicationPolicy(policyName, modifiedPolicy);
                    IsilonSyncIQJob isiSyncJobcancel = null;
                    try {
                        isiSyncJobcancel = new IsilonSyncIQJob(policyName, system.getId(), taskCompleter, policyName);
                        ControllerServiceImpl.enqueueJob(new QueueJob(isiSyncJobcancel));
                        return BiosCommandResult.createPendingResult();
                    } catch (Exception ex) {
                        _log.error("Cancel Replication Job Failed ", ex);
                        ServiceError error = DeviceControllerErrors.isilon.jobFailed("Cancel Replication Job Failed as:" + ex.getMessage());
                        if (taskCompleter != null) {
                            taskCompleter.error(_dbClient, error);
                        }
                        return BiosCommandResult.createErrorResult(error);
                    }
                } else {
                    return BiosCommandResult.createSuccessfulResult();
                }
            }
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            return BiosCommandResult.createErrorResult(e);
        }

    }

    public BiosCommandResult doCancelReplicationPolicy(StorageSystem system, String policyName) {
        try {
            IsilonApi isi = getIsilonDevice(system);
            IsilonSyncPolicy policy = isi.getReplicationPolicy(policyName);
            JobState policyState = policy.getLastJobState();

            if (policyState.equals(JobState.running) || policyState.equals(JobState.paused)) {
                _log.info("Canceling Replication Policy  -{} because policy is in - {} state ", policyName, policyState);
                IsilonSyncPolicy modifiedPolicy = new IsilonSyncPolicy();
                modifiedPolicy.setName(policyName);
                modifiedPolicy.setLastJobState(JobState.canceled);
                isi.modifyReplicationPolicy(policyName, modifiedPolicy);
                return BiosCommandResult.createSuccessfulResult();

            } else {
                _log.error("Replication Policy - {} can't be CANCEL because policy's last job is in {} state", policyName,
                        policyState);
                ServiceError error = DeviceControllerErrors.isilon
                        .jobFailed(
                                "doCancelReplicationPolicy as : Replication Policy Job can't be Cancel because policy's last job is NOT in PAUSED state");
                return BiosCommandResult.createErrorResult(error);
            }
        } catch (IsilonException e) {
            return BiosCommandResult.createErrorResult(e);
        }

    }

    /**
     * Get isilon device represented by the StorageDevice
     * 
     * @param device
     *            StorageDevice object
     * @return IsilonApi object
     * @throws IsilonException
     */
    private IsilonApi getIsilonDevice(StorageSystem device) throws IsilonException {
        IsilonApi isilonAPI;
        URI deviceURI;
        try {
            deviceURI = new URI("https", null, device.getIpAddress(), device.getPortNumber(), "/", null, null);
        } catch (URISyntaxException ex) {
            throw IsilonException.exceptions.errorCreatingServerURL(device.getIpAddress(), device.getPortNumber(), ex);
        }
        if (device.getUsername() != null && !device.getUsername().isEmpty()) {
            isilonAPI = _factory.getRESTClient(deviceURI, device.getUsername(), device.getPassword());
        } else {
            isilonAPI = _factory.getRESTClient(deviceURI);
        }

        return isilonAPI;

    }

    /**
     * Call to device to delete the policy
     * 
     * @param system
     * @param policyName
     * @return
     */
    public BiosCommandResult dodeleteReplicationPolicy(StorageSystem system, String policyName) {
        try {
            IsilonApi isi = getIsilonDevice(system);
            IsilonSyncPolicy policy = isi.getReplicationPolicy(policyName);
            JobState policyState = policy.getLastJobState();

            if (policyState.equals(JobState.running) || policyState.equals(JobState.paused)) {
                _log.info("Canceling Replication Policy  -{} because policy is in - {} state ", policyName, policyState);
                IsilonSyncPolicy modifiedPolicy = new IsilonSyncPolicy();
                modifiedPolicy.setName(policyName);
                modifiedPolicy.setLastJobState(JobState.canceled);
                isi.modifyReplicationPolicy(policyName, modifiedPolicy);
            }
            isi.deleteReplicationPolicy(policyName);
            _log.info("dodeleteReplicationPolicy - {} finished succesfully", policy.toString());
            _log.info("Sleeping for 10 seconds for detach mirror to complete...");
            TimeUnit.SECONDS.sleep(10);
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            return BiosCommandResult.createErrorResult(e);
        } catch (InterruptedException e) {
            _log.warn("dodeleteReplicationPolicy - {} intertupted");
            return BiosCommandResult.createSuccessfulResult();
        }

    }

    /**
     * Call to device to delete policy
     * 
     * @param system
     * @param policyName
     * @return
     */
    public BiosCommandResult doStopReplicationPolicy(StorageSystem system, String policyName) {
        try {
            IsilonApi isi = getIsilonDevice(system);
            IsilonSyncPolicy policy = isi.getReplicationPolicy(policyName);
            if (policy.getEnabled()) {
                IsilonSyncPolicy modifiedPolicy = new IsilonSyncPolicy();
                modifiedPolicy.setName(policyName);
                modifiedPolicy.setEnabled(false);
                isi.modifyReplicationPolicy(policyName, modifiedPolicy);
                return BiosCommandResult.createSuccessfulResult();
            } else {
                _log.info("Replication Policy - {} can't be STOPPED because policy is already DISABLED", policy.toString());
                return BiosCommandResult.createSuccessfulResult();
            }
        } catch (IsilonException e) {
            return BiosCommandResult.createErrorResult(e);
        }
    }

    /**
     * Call to device to failover the policy
     * 
     * @param system
     * @param policyName
     * @param taskCompleter
     * @return
     */
    public BiosCommandResult doFailover(StorageSystem system, String policyName, TaskCompleter taskCompleter) {
        _log.info("IsilonMirrorOperations -  doFailover started ");
        try {
            IsilonApi isi = getIsilonDevice(system);
            IsilonSyncJob job = new IsilonSyncJob();
            job.setId(policyName);
            job.setAction(Action.allow_write);

            isi.modifyReplicationJob(job);

            IsilonSyncJobFailover isiSyncJobFailover = new IsilonSyncJobFailover(policyName, system.getId(), taskCompleter, policyName);
            try {
                ControllerServiceImpl.enqueueJob(new QueueJob(isiSyncJobFailover));
                return BiosCommandResult.createPendingResult();
            } catch (Exception ex) {
                _log.error("Failover to Secondary Cluster Failed", ex);
                ServiceError error = DeviceControllerErrors.isilon.jobFailed("Failover to Secondary Cluster Failed as :" + ex.getMessage());
                if (taskCompleter != null) {
                    taskCompleter.error(_dbClient, error);
                }
                return BiosCommandResult.createErrorResult(error);
            }
        } catch (IsilonException e) {
            return BiosCommandResult.createErrorResult(e);
        }
    }

    /**
     * Call to device to resync prep the policy
     * 
     * @param primarySystem
     * @param secondarySystem
     * @param policyName
     * @param completer
     * @return
     * @throws IsilonException
     */
    public BiosCommandResult isiResyncPrep(StorageSystem primarySystem, StorageSystem secondarySystem, String policyName,
            TaskCompleter completer)
            throws IsilonException {
        _log.info("IsilonMirrorOperations -  isiResyncPrep source system {} and dest system {} - start ",
                primarySystem.getLabel(), secondarySystem.getLabel());
        IsilonApi isiPrimary = getIsilonDevice(primarySystem);
        IsilonSyncJob job = new IsilonSyncJob();
        job.setId(policyName);
        job.setAction(Action.resync_prep);

        IsilonSyncPolicy policy = isiPrimary.getReplicationPolicy(policyName);

        IsilonApi isiSecondary = null;
        IsilonSyncTargetPolicy targetPolicy = null;

        // we need to enable to policy, before running resync-prep
        if (!policy.getEnabled()) {
            policy = doEnableReplicationPolicy(isiPrimary, policyName);
        }

        isiSecondary = getIsilonDevice(secondarySystem);
        targetPolicy = isiSecondary.getTargetReplicationPolicy(policyName);
        // already resync is created then we can start policy
        if (targetPolicy.getFoFbState().equals(FOFB_STATES.resync_policy_created)
                && targetPolicy.getLastJobState().equals(JobState.finished)) {
            return BiosCommandResult.createSuccessfulResult();
        }

        // if policy enable then we run resync operation
        if (policy.getEnabled()) {
            isiPrimary.modifyReplicationJob(job);

            IsilonSyncJobResync isilonSyncJobResync = new IsilonSyncJobResync(policyName, secondarySystem.getId(), completer, policyName);

            try {
                ControllerServiceImpl.enqueueJob(new QueueJob(isilonSyncJobResync));
                return BiosCommandResult.createPendingResult();
            } catch (Exception ex) {
                _log.error("Resync-Prep to Secondary Cluster Failed", ex);
                ServiceError error = DeviceControllerErrors.isilon.jobFailed("Resync-Prep FAILED  as : " + ex.getMessage());
                if (completer != null) {
                    completer.error(_dbClient, error);
                }
                return BiosCommandResult.createErrorResult(error);
            }
        }

        return BiosCommandResult.createSuccessfulResult();
    }

    /**
     * Call to device to fail back policy
     * 
     * @param primarySystem
     * @param secondarySystem
     * @param policyName
     * @param taskCompleter
     * @return
     */
    public BiosCommandResult doFailBack(StorageSystem primarySystem, StorageSystem secondarySystem, String policyName,
            TaskCompleter taskCompleter) {

        String mirrorPolicyName = policyName.concat("_mirror");
        BiosCommandResult result;

        try {
            /*
             * Step 1. Creates a mirror replication policy for the secondary cluster i.e Resync-prep on primary cluster , this will disable
             * primary cluster replication policy.
             */

            result = isiResyncPrep(primarySystem, secondarySystem, policyName, null);
            if (!result.isCommandSuccess()) {
                return result;
            }

            /*
             * Step 2. Start the mirror replication policy manually, this will replicate new data (written during failover) from secondary
             * cluster to primary cluster.
             */

            result = doStartReplicationPolicy(secondarySystem, mirrorPolicyName, null);
            if (!result.isCommandSuccess()) {
                return result;
            }

            /*
             * Step 3. Allow Write on Primary Cluster local target after replication from step 2
             * i.e Fail over to Primary Cluster
             */

            result = doFailover(primarySystem, mirrorPolicyName, null);
            if (!result.isCommandSuccess()) {
                return result;
            }

            /*
             * Step 4. Resync-Prep on secondary cluster , same as step 1 but will be executed on secondary cluster instead of primary
             * cluster.
             */

            result = isiResyncPrep(secondarySystem, primarySystem, mirrorPolicyName, null);
            if (!result.isCommandSuccess()) {
                return result;
            }

            _log.info("Failback from cluster {} to cluster {} successfully finished", secondarySystem.getIpAddress(),
                    primarySystem.getIpAddress());
            return BiosCommandResult.createSuccessfulResult();

        } catch (IsilonException e) {
            return BiosCommandResult.createErrorResult(e);
        }
    }

    public String isiGetReportErrMsg(List<IsilonSyncPolicyReport> policyReports) {
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

    public void refreshMirrorFileShareLink(StorageSystem system, FileShare source, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
        MirrorFileRefreshTaskCompleter mirrorRefreshCompleter = (MirrorFileRefreshTaskCompleter) completer;
        String policyName = target.getLabel();
        IsilonSyncPolicy policy;
        IsilonSyncTargetPolicy localTarget = null;
        StorageSystem systemTarget = _dbClient.queryObject(StorageSystem.class, target.getStorageDevice());
        IsilonApi isiPrimary = getIsilonDevice(system);
        IsilonApi isiSecondary = getIsilonDevice(systemTarget);
        try {

            policy = isiPrimary.getReplicationPolicy(policyName);
            if (policy.getLastStarted() != null) {
                localTarget = isiSecondary.getTargetReplicationPolicy(policyName);
            }
            if (policy.getLastStarted() == null) {
                mirrorRefreshCompleter.setFileMirrorStatusForSuccess(FileShare.MirrorStatus.UNKNOWN);
            } else if (!policy.getEnabled()) {
                mirrorRefreshCompleter.setFileMirrorStatusForSuccess(FileShare.MirrorStatus.DETACHED);
            } else if (policy.getLastJobState().equals(JobState.paused)) {
                mirrorRefreshCompleter.setFileMirrorStatusForSuccess(FileShare.MirrorStatus.SUSPENDED);
            } else if (localTarget.getFoFbState().equals(FOFB_STATES.writes_enabled)) {
                mirrorRefreshCompleter.setFileMirrorStatusForSuccess(FileShare.MirrorStatus.FAILED_OVER);
            } else if (policy.getEnabled() && policy.getLastJobState().equals(JobState.finished) &&
                    localTarget.getFoFbState().equals(FOFB_STATES.writes_disabled)) {
                mirrorRefreshCompleter.setFileMirrorStatusForSuccess(FileShare.MirrorStatus.SYNCHRONIZED);
            } else if (policy.getLastJobState().equals(JobState.running)) {
                mirrorRefreshCompleter.setFileMirrorStatusForSuccess(FileShare.MirrorStatus.IN_SYNC);
            } else if (policy.getLastJobState().equals(JobState.failed) || policy.getLastJobState().equals(JobState.needs_attention)) {
                mirrorRefreshCompleter.setFileMirrorStatusForSuccess(FileShare.MirrorStatus.ERROR);
            }
            completer.ready(_dbClient);
        } catch (IsilonException e) {
            completer.error(_dbClient, BiosCommandResult.createErrorResult(e).getServiceCoded());
        }
    }

}
