/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.isilon;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.isilon.restapi.IsilonApi;
import com.emc.storageos.isilon.restapi.IsilonApiFactory;
import com.emc.storageos.isilon.restapi.IsilonException;
import com.emc.storageos.isilon.restapi.IsilonSshApi;
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
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.file.FileMirrorOperations;
import com.emc.storageos.volumecontroller.impl.file.MirrorFileRefreshTaskCompleter;
import com.emc.storageos.volumecontroller.impl.isilon.job.IsilonSyncJobFailover;
import com.emc.storageos.volumecontroller.impl.isilon.job.IsilonSyncJobResync;
import com.emc.storageos.volumecontroller.impl.isilon.job.IsilonSyncJobStart;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class IsilonMirrorOperations implements FileMirrorOperations {
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

    /**
     * Create Mirror between source and target fileshare
     */
    @Override
    public void createMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {
        FileShare sourceFileShare = _dbClient.queryObject(FileShare.class, source);
        FileShare targetFileShare = _dbClient.queryObject(FileShare.class, target);

        StorageSystem sourceStorageSystem = _dbClient.queryObject(StorageSystem.class, sourceFileShare.getStorageDevice());
        StorageSystem targetStorageSystem = _dbClient.queryObject(StorageSystem.class, targetFileShare.getStorageDevice());

        String policyName = targetFileShare.getLabel();

        VirtualPool virtualPool = _dbClient.queryObject(VirtualPool.class, sourceFileShare.getVirtualPool());

        String schedule = null;
        if (virtualPool != null) {
            if (virtualPool.getFrRpoValue() == 0) {
                // Zero RPO value means policy has to be started manually-NO Schedule
                schedule = "";
            } else {
                schedule = createSchedule(virtualPool.getFrRpoValue().toString(), virtualPool.getFrRpoType());
            }
        }
        BiosCommandResult cmdResult = doCreateReplicationPolicy(sourceStorageSystem, policyName, sourceFileShare.getPath(),
                targetStorageSystem.getIpAddress(), targetFileShare.getPath(), IsilonSyncPolicy.Action.sync, "", schedule);
        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }
    }

    @Override
    public void stopMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {
        BiosCommandResult cmdResult = null;
        if (target.getParentFileShare() != null) {
            String policyName = target.getLabel();
            cmdResult = this.doStopReplicationPolicy(system, policyName);
            if (cmdResult.getCommandSuccess()) {
                completer.ready(_dbClient);
            } else {
                completer.error(_dbClient, cmdResult.getServiceCoded());
            }
        }
    }

    @Override
    public void startMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException {
        BiosCommandResult cmdResult = null;
        cmdResult = doStartReplicationPolicy(system, policyName, completer);
        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else if (cmdResult.getCommandPending()) {
            completer.statusPending(_dbClient, cmdResult.getMessage());
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }

    }

    @Override
    public void pauseMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {
        BiosCommandResult cmdResult = null;
        if (target.getParentFileShare() != null) {
            String policyName = target.getLabel();
            IsilonApi isi = getIsilonDevice(system);
            IsilonSyncPolicy policy = isi.getReplicationPolicy(policyName);
            JobState policyState = policy.getLastJobState();

            if (policyState.equals(JobState.running) || policyState.equals(JobState.paused)) {
                doCancelReplicationPolicy(system, policyName);
            }
            cmdResult = doStopReplicationPolicy(system, policyName);
            if (cmdResult.getCommandSuccess()) {
                completer.ready(_dbClient);
            } else {
                completer.error(_dbClient, cmdResult.getServiceCoded());
            }
        }
    }

    @Override
    public void resumeMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
        BiosCommandResult cmdResult = null;
        if (target.getParentFileShare() != null) {
            String policyName = target.getLabel();
            cmdResult = doStartReplicationPolicy(system, policyName, completer);
            if (cmdResult.getCommandSuccess()) {
                completer.ready(_dbClient);
            } else if (cmdResult.getCommandPending()) {
                completer.statusPending(_dbClient, cmdResult.getMessage());
            } else {
                completer.error(_dbClient, cmdResult.getServiceCoded());
            }
        }
    }

    @Override
    public void cancelMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
        FileShare sourceFileShare = _dbClient.queryObject(FileShare.class, target.getParentFileShare().getURI());
        String policyName = ControllerUtils.generateLabel(sourceFileShare.getLabel(), target.getLabel());
        BiosCommandResult cmdResult = doCancelReplicationPolicy(system, policyName);
        if (cmdResult.getCommandSuccess()) {
            WorkflowStepCompleter.stepSucceded(completer.getOpId());
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }
    }

    @Override
    public void deleteMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {
        FileShare targetFileShare = _dbClient.queryObject(FileShare.class, target);
        String policyName = targetFileShare.getLabel();
        BiosCommandResult cmdResult = dodeleteReplicationPolicy(system, policyName);
        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
            WorkflowStepCompleter.stepSucceded(completer.getOpId());
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }
    }

    @Override
    public void failoverMirrorFileShareLink(StorageSystem systemTarget, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException {
        BiosCommandResult cmdResult = null;
        cmdResult = this.doFailover(systemTarget, policyName, completer);
        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else if (cmdResult.getCommandPending()) {
            completer.statusPending(_dbClient, cmdResult.getMessage());
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }
    }

    @Override
    public void
            resyncMirrorFileShareLink(StorageSystem primarySystem, StorageSystem secondarySystem, FileShare target,
                    TaskCompleter completer, String policyName) {
        BiosCommandResult cmdResult = null;
        cmdResult = isiResyncPrep(primarySystem, secondarySystem, policyName, completer);
        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else if (cmdResult.getCommandPending()) {
            completer.statusPending(_dbClient, cmdResult.getMessage());
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
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
     * Get isilon device represented by the StorageDevice
     * 
     * @param StorageSystem
     *            object
     * @return IsilonSshApi object
     */
    private IsilonSshApi getIsilonDeviceSsh(StorageSystem device) throws IsilonException {
        IsilonSshApi sshDmApi = new IsilonSshApi();
        sshDmApi.setConnParams(device.getIpAddress(), device.getUsername(), device.getPassword());
        return sshDmApi;
    }

    // mirror related operation
    /**
     * Call to Isilon Device to Create Replication Session
     * 
     * @param system
     * @param name
     * @param source_root_path
     * @param target_host
     * @param target_path
     * @param action
     * @param description
     * @param schedule
     * @return
     */
    public BiosCommandResult doCreateReplicationPolicy(StorageSystem system, String name, String source_root_path,
            String target_host, String target_path, IsilonSyncPolicy.Action action, String description,
            String schedule) {
        try {
            _log.info("IsilonFileStorageDevice doCreateReplicationPolicy {} - start", source_root_path);
            IsilonApi isi = getIsilonDevice(system);

            IsilonSyncPolicy policy = new IsilonSyncPolicy(name, source_root_path, target_path, target_host, action);
            if (schedule != null && !schedule.isEmpty()) {
                policy.setSchedule(schedule);
            }
            if (description != null && !description.isEmpty()) {
                policy.setDescription(description);
            }
            policy.setEnabled(false);
            String policyId = isi.createReplicationPolicy(policy);
            _log.info("IsilonFileStorageDevice doCreateReplicationPolicy {} with policyId {} - complete", name,
                    policyId);
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            return BiosCommandResult.createErrorResult(e);
        }
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
        try {

            IsilonApi isi = getIsilonDevice(system);
            IsilonSyncPolicy policy = isi.getReplicationPolicy(policyName);
            IsilonSyncPolicy.JobState policyState = policy.getLastJobState();

            if (!policy.getEnabled()) {
                IsilonSyncPolicy modifiedPolicy = new IsilonSyncPolicy();
                modifiedPolicy.setName(policyName);
                modifiedPolicy.setEnabled(true);

                isi.modifyReplicationPolicy(policyName, modifiedPolicy);
                policy = isi.getReplicationPolicy(policyName);
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
     * Call to Isilon to pause the policy
     * 
     * @param system
     * @param policyName
     * @return
     */
    public BiosCommandResult doPauseReplicationPolicy(StorageSystem system, String policyName) {
        try {
            IsilonApi isi = getIsilonDevice(system);
            IsilonSyncPolicy policy = isi.getReplicationPolicy(policyName);
            JobState policyState = policy.getLastJobState();

            if (policyState.equals(JobState.running)) {
                IsilonSshApi sshDmApi = getIsilonDeviceSsh(system);
                sshDmApi.executeSsh("sync jobs" + " " + "pause" + " " + policyName, "");
                _log.info("doPauseReplicationPolicy for replication policy {}- finished successfully", policyName);
                return BiosCommandResult.createSuccessfulResult();

            } else {
                _log.error("Replication Policy - {} can't be PAUSED because policy's last job is in {} state", policyName,
                        policyState);
                ServiceError error = DeviceControllerErrors.isilon
                        .jobFailed(
                                "doResumeReplicationPolicy as : Replication Policy Job can't be PAUSED because policy's last job is NOT in RUNNING state");
                return BiosCommandResult.createErrorResult(error);
            }
        } catch (IsilonException e) {
            return BiosCommandResult.createErrorResult(e);
        }
    }

    /**
     * Call to device to resume policy
     * 
     * @param system
     * @param policyName
     * 
     */
    public BiosCommandResult doResumeReplicationPolicy(StorageSystem system, String policyName) {
        try {
            IsilonApi isi = getIsilonDevice(system);
            IsilonSyncPolicy policy = isi.getReplicationPolicy(policyName);
            if (!policy.getEnabled()) {
                IsilonSyncPolicy modifiedPolicy = new IsilonSyncPolicy();
                modifiedPolicy.setName(policyName);
                modifiedPolicy.setEnabled(true);

                isi.modifyReplicationPolicy(policyName, modifiedPolicy);

                _log.info("Replication Policy - {} ENABLED successfully", policy.toString());
                return BiosCommandResult.createSuccessfulResult();
            } else {
                ServiceError error = DeviceControllerErrors.isilon
                        .jobFailed("doResumeReplicationPolicy as : Replication Policy can't be RESUMED because "
                                + "policy is already in Active state");
                return BiosCommandResult.createErrorResult(error);
            }
        } catch (IsilonException e) {
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
                IsilonSshApi sshDmApi = getIsilonDeviceSsh(system);
                sshDmApi.executeSsh("sync jobs" + " " + "cancel" + " " + policyName, "");
            }
            isi.deleteReplicationPolicy(policyName);
            _log.info("dodeleteReplicationPolicy - {} finished succesfully", policy.toString());
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            return BiosCommandResult.createErrorResult(e);
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
                _log.error("Replication Policy - {} can't be STOPPED because policy is already DISABLED", policy.toString());
                ServiceError error = DeviceControllerErrors.isilon
                        .jobFailed("doStopReplicationPolicy as : Replication Policy can't be STOPPED because policy is already DISABLED");
                return BiosCommandResult.createErrorResult(error);
            }
        } catch (IsilonException e) {
            return BiosCommandResult.createErrorResult(e);
        }

    }

    /**
     * Call to device to my the RPO of policy
     * 
     * @param system
     * @param policyName
     * @param RPO
     * @return
     */
    public BiosCommandResult doModifyReplicationPolicy(StorageSystem system, String policyName, String RPO) {
        try {
            IsilonApi isi = getIsilonDevice(system);
            IsilonSyncPolicy policy = isi.getReplicationPolicy(policyName);
            JobState policyState = policy.getLastJobState();

            if (!policyState.equals(JobState.running) && !policyState.equals(JobState.paused)) {
                IsilonSyncPolicy modifiedPolicy = new IsilonSyncPolicy();
                modifiedPolicy.setSchedule(RPO);
                modifiedPolicy.setName(policyName);
                isi.modifyReplicationPolicy(policyName, modifiedPolicy);
                return BiosCommandResult.createSuccessfulResult();
            } else {
                _log.error("Replication Policy - {} can't be MODIFIED because policy has an active job", policy.toString());
                ServiceError error = DeviceControllerErrors.isilon
                        .jobFailed("doModifyReplicationPolicy as : The policy has an active job and cannot be modified.");
                return BiosCommandResult.createErrorResult(error);
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

        IsilonApi isiPrimary = getIsilonDevice(primarySystem);
        IsilonSyncJob job = new IsilonSyncJob();
        job.setId(policyName);
        job.setAction(Action.resync_prep);

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

    private String createSchedule(String fsRpoValue, String fsRpoType) {
        StringBuilder builder = new StringBuilder();
        switch (fsRpoType) {
            case "MINUTES":
                builder.append("every 1 days every ");
                builder.append(fsRpoValue);
                builder.append(" minutes between 12:00 AM and 11:59 PM");
                break;
            case "HOURS":
                builder.append("every 1 days every ");
                builder.append(fsRpoValue);
                builder.append(" hours between 12:00 AM and 11:59 PM");
                break;
            case "DAYS":
                builder.append("every ");
                builder.append(fsRpoValue);
                builder.append(" days at 12:00 AM");
                break;
        }
        return builder.toString();
    }

    @Override
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
            if (policy.getLastStarted() == null && !policy.getEnabled()) {
                mirrorRefreshCompleter.setFileMirrorStatusForSuccess(FileShare.MirrorStatus.UNKNOWN);
            } else if (policy.getLastStarted() != null && !policy.getEnabled()) {
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
