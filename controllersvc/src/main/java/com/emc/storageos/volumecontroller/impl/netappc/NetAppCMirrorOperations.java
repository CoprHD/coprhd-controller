/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.netappc;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.netappc.NetAppClusterApi;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.file.FileMirrorOperations;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.netappc.job.NetAppCSnapMirrorJob;
import com.emc.storageos.volumecontroller.impl.netappc.job.NetAppSnapMirrorAbortJob;
import com.emc.storageos.volumecontroller.impl.netappc.job.NetAppSnapMirrorDestroyJob;
import com.emc.storageos.volumecontroller.impl.netappc.job.NetAppSnapMirrorReleaseJob;
import com.iwave.ext.netappc.model.SnapMirrorVolumeStatus;
import com.iwave.ext.netappc.model.SnapmirrorCreateParam;
import com.iwave.ext.netappc.model.SnapmirrorCronScheduleInfo;
import com.iwave.ext.netappc.model.SnapmirrorInfo;
import com.iwave.ext.netappc.model.SnapmirrorInfoResp;
import com.iwave.ext.netappc.model.SnapmirrorResp;
import com.iwave.ext.netappc.model.SnapmirrorState;

public class NetAppCMirrorOperations implements FileMirrorOperations {
    private static final Logger _log = LoggerFactory
            .getLogger(NetAppCMirrorOperations.class);

    private DbClient _dbClient;

    public DbClient get_dbClient() {
        return _dbClient;
    }

    public void set_dbClient(DbClient _dbClient) {
        this._dbClient = _dbClient;
    }

    // Default constructor
    public NetAppCMirrorOperations() {
    }

    @Override
    public void createMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {

        FileShare sourceFs = _dbClient.queryObject(FileShare.class, source);
        FileShare targetFs = _dbClient.queryObject(FileShare.class, target);

        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceFs.getStorageDevice());
        StorageSystem targetSystem = _dbClient.queryObject(StorageSystem.class, targetFs.getStorageDevice());

        VirtualPool virtualPool = _dbClient.queryObject(VirtualPool.class, sourceFs.getVirtualPool());

        BiosCommandResult cmdResult = doCreateSnapMirror(sourceSystem, targetSystem, sourceFs, targetFs, virtualPool, completer);

        updateTaskStatus(cmdResult, completer);
    }

    @Override
    public void startMirrorFileShareLink(StorageSystem sourceSystem, FileShare targetFs, TaskCompleter completer, String policyName)
            throws DeviceControllerException {
        _log.info("NetAppCMirrorOperations -  startMirrorFileShareLink started ");

        FileShare sourceFs = _dbClient.queryObject(FileShare.class, targetFs.getParentFileShare().getURI());
        StorageSystem targetSystem = _dbClient.queryObject(StorageSystem.class, targetFs.getStorageDevice());

        BiosCommandResult cmdResult = doInitialiseSnapMirror(sourceSystem, targetSystem, sourceFs, targetFs, completer);

        updateTaskStatus(cmdResult, completer);

        _log.info("NetappCMirrorFileOperations -  startMirrorFileShareLink source file {} and dest file {} - complete ",
                sourceFs.getName(), targetFs.getName());
    }

    @Override
    public void failoverMirrorFileShareLink(StorageSystem targetSystem, FileShare targetFs, TaskCompleter completer, String policyName)
            throws DeviceControllerException {
        BiosCommandResult cmdResult = null;
        FileShare sourceFs = _dbClient.queryObject(FileShare.class, targetFs.getParentFileShare().getURI());
        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceFs.getStorageDevice());

        cmdResult = doFailoverSnapMirror(sourceSystem, targetSystem, sourceFs, targetFs, completer);
        updateTaskStatus(cmdResult, completer);
    }

    @Override
    public void stopMirrorFileShareLink(StorageSystem targetSystem, FileShare targetFs, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        // TODO Auto-generated method stub
        FileShare sourceFs = _dbClient.queryObject(FileShare.class, targetFs.getParentFileShare().getURI());
        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceFs.getStorageDevice());

        BiosCommandResult cmdResult = doDestroySnapMirror(sourceSystem, targetSystem, sourceFs, targetFs, taskCompleter);
        updateTaskStatus(cmdResult, taskCompleter);
    }

    @Override
    public void deleteMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        BiosCommandResult cmdResult = null;

        FileShare sourceFs = _dbClient.queryObject(FileShare.class, source);
        FileShare targetFs = _dbClient.queryObject(FileShare.class, target);

        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceFs.getStorageDevice());
        StorageSystem targetSystem = _dbClient.queryObject(StorageSystem.class, targetFs.getStorageDevice());

        cmdResult = doDestroySnapMirror(sourceSystem, targetSystem, sourceFs, targetFs, taskCompleter);
        updateTaskStatus(cmdResult, taskCompleter);
    }

    @Override
    public void pauseMirrorFileShareLink(StorageSystem targetSystem, FileShare targetFs, TaskCompleter completer)
            throws DeviceControllerException {

        FileShare sourceFs = _dbClient.queryObject(FileShare.class, targetFs.getParentFileShare().getURI());
        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceFs.getStorageDevice());

        BiosCommandResult cmdResult =
                doQuiesceSnapMirror(sourceSystem, targetSystem, sourceFs, targetFs, completer);

        updateTaskStatus(cmdResult, completer);
    }

    @Override
    public void resumeMirrorFileShareLink(StorageSystem targetSystem, FileShare targetFs, TaskCompleter completer)
            throws DeviceControllerException {
        FileShare sourceFs = _dbClient.queryObject(FileShare.class, targetFs.getParentFileShare().getURI());
        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceFs.getStorageDevice());

        BiosCommandResult cmdResult =
                doResumeSnapMirror(sourceSystem, targetSystem, sourceFs, targetFs, completer);

        updateTaskStatus(cmdResult, completer);
    }

    @Override
    public void resyncMirrorFileShareLink(StorageSystem primarySystem, StorageSystem secondarySystem, FileShare target,
            TaskCompleter completer, String policyName) {

    }

    // cancel or abort mirror link operation
    @Override
    public void cancelMirrorFileShareLink(StorageSystem targetSystem, FileShare targetFs, TaskCompleter completer)
            throws DeviceControllerException {
        FileShare sourceFs = _dbClient.queryObject(FileShare.class, targetFs.getParentFileShare().getURI());
        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceFs.getStorageDevice());

        BiosCommandResult cmdResult =
                doAbortSnapMirror(sourceSystem, targetSystem, sourceFs, targetFs, completer);

        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else if (cmdResult.getCommandPending()) {
            completer.statusPending(_dbClient, cmdResult.getMessage());
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }
    }

    @Override
    public void refreshMirrorFileShareLink(StorageSystem system, FileShare source, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
    }

    @Override
    public void doModifyReplicationRPO(StorageSystem system, Long rpoValue, String rpoType, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
        //
    }

    /**
     * Create SnapMirror relationship and
     * The snapmirror-create API must be issued on the destination cluster
     * 
     * @param sourceSystem
     * @param targetSystem
     * @param sourceFs
     * @param targetFs
     * @param virtualPool
     * @param taskCompleter
     * @return
     */
    BiosCommandResult doCreateSnapMirror(StorageSystem sourceSystem, StorageSystem targetSystem, FileShare sourceFs, FileShare targetFs,
            VirtualPool virtualPool, TaskCompleter taskCompleter) {

        SnapmirrorInfo snapMirrorInfo = prepareSnapMirrorInfo(sourceSystem, targetSystem, sourceFs, targetFs);
        _log.info("NetAppCMirrorOperations - doCreateSnapMirror sourcelocation {} and destinationlocation {}- start",
                snapMirrorInfo.getSourceLocation(), snapMirrorInfo.getDestinationLocation());

        // set schedule policy name
        SnapmirrorInfoResp snapMirrorResult = null;
        SnapmirrorCreateParam snapMirrorCreateParam = new SnapmirrorCreateParam(snapMirrorInfo);

        try {
            NetAppClusterApi ncApi = getNetAppcClient(targetSystem, targetFs);

            // create cron schedule policy
            SnapmirrorCronScheduleInfo scheduleInfo = doCreateCronSchedule(ncApi, virtualPool.getFrRpoValue().toString(),
                    virtualPool.getFrRpoType(), targetFs.getLabel());

            if (scheduleInfo != null) {
                snapMirrorCreateParam.setCronScheduleName(scheduleInfo.getJobScheduleName());
            } else {
                ServiceError error =
                        DeviceControllerErrors.netappc.jobFailed("Snapmirror create cron schedule is failed");
                return BiosCommandResult.createErrorResult(error);
            }

            // call create snap mirror relation ship
            snapMirrorResult = ncApi.createSnapMirror(snapMirrorCreateParam);

            if (snapMirrorResult != null) {
                NetAppCSnapMirrorJob snapMirrorCreateJob = new NetAppCSnapMirrorJob(snapMirrorInfo, targetSystem.getId(), taskCompleter,
                        SnapmirrorState.READY.toString());
                ControllerServiceImpl.enqueueJob(new QueueJob(snapMirrorCreateJob));
                _log.info("NetAppCMirrorOperations - doCreateSnapMirror {} with policy state {} - complete",
                        snapMirrorResult.getScheduleName(), snapMirrorResult.getMirrorState().toString());
                return BiosCommandResult.createPendingResult();
            } else {
                ServiceError error =
                        DeviceControllerErrors.netappc.jobFailed("Snapmirror Create failed");
                return BiosCommandResult.createErrorResult(error);
            }

        } catch (Exception e) {
            _log.error("Snapmirror create failed", e);
            ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror Create failed:" + e.getMessage());
            return BiosCommandResult.createErrorResult(error);
        }
    }

    /**
     * Performs the initial update of a SnapMirror relationship
     * and this API must be used from the destination storage system
     * 
     * @param sourceSystem
     * @param targetSystem
     * @param sourceFs
     * @param targetFs
     * @param taskCompleter
     * @return
     */
    BiosCommandResult
            doInitialiseSnapMirror(StorageSystem sourceSystem, StorageSystem targetSystem, FileShare sourceFs, FileShare targetFs,
                    TaskCompleter taskCompleter) {
        SnapmirrorInfo snapMirrorInfo = prepareSnapMirrorInfo(sourceSystem, targetSystem, sourceFs, targetFs);
        _log.info("NetAppCMirrorOperations - doInitialiseSnapMirror sourcelocation {} and destinationlocation {}- start",
                snapMirrorInfo.getSourceLocation(), snapMirrorInfo.getDestinationLocation());

        try {
            NetAppClusterApi ncApi = getNetAppcClient(targetSystem, targetFs);
            // get the snapmirror state
            SnapmirrorInfoResp mirrorInfoResp = ncApi.getSnapMirrorInfo(snapMirrorInfo);
            if (SnapmirrorState.UNKNOWN.equals(mirrorInfoResp.getMirrorState()) ||
                    SnapmirrorState.READY.equals(mirrorInfoResp.getMirrorState())) {

                // perform initial update of snapmirror relationship
                ncApi.initialiseSnapMirror(snapMirrorInfo);

                NetAppCSnapMirrorJob netappCSnapMirrorJob = new NetAppCSnapMirrorJob(snapMirrorInfo, targetSystem.getId(), taskCompleter,
                        SnapmirrorState.SYNCRONIZED.toString());
                ControllerServiceImpl.enqueueJob(new QueueJob(netappCSnapMirrorJob));
                return BiosCommandResult.createPendingResult();
            } else {
                _log.error("Snapmirror start failed");
                ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror Start operation failed:");
                return BiosCommandResult.createErrorResult(error);

            }
        } catch (Exception e) {
            _log.error("Snapmirror start failed", e);
            ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror Start failed:" + e.getMessage());
            return BiosCommandResult.createErrorResult(error);
        }
    }

    /**
     * performs a failover to the destination fileshare
     * 
     * @param sourceSystem
     * @param targetSystem
     * @param sourceFs
     * @param targetFs
     * @param taskCompleter
     * @return
     */
    BiosCommandResult doFailoverSnapMirror(StorageSystem sourceSystem, StorageSystem targetSystem, FileShare sourceFs, FileShare targetFs,
            TaskCompleter taskCompleter) {

        SnapmirrorInfo snapMirrorInfo = prepareSnapMirrorInfo(sourceSystem, targetSystem, sourceFs, targetFs);

        _log.info("NetAppCMirrorOperations - doFailoverSnapMirror sourcelocation {} and destinationlocation {}- start",
                snapMirrorInfo.getSourceLocation(), snapMirrorInfo.getDestinationLocation());

        try {
            NetAppClusterApi ncApi = getNetAppcClient(targetSystem, targetFs);

            SnapmirrorInfoResp mirrorInfoResp = ncApi.getSnapMirrorInfo(snapMirrorInfo);

            // set relationship id
            snapMirrorInfo.setRelationshipId(mirrorInfoResp.getRelationshipId());

            if (SnapmirrorState.PAUSED.equals(mirrorInfoResp.getMirrorState()) ||
                    SnapmirrorState.SYNCRONIZED.equals(mirrorInfoResp.getMirrorState())) {
                // perform failover on destination fileshare
                ncApi.breakSnapMirror(snapMirrorInfo);
                NetAppCSnapMirrorJob netappCSnapMirrorJob = new NetAppCSnapMirrorJob(snapMirrorInfo, targetSystem.getId(), taskCompleter,
                        SnapmirrorState.FAILOVER.toString());
                ControllerServiceImpl.enqueueJob(new QueueJob(netappCSnapMirrorJob));
            } else {
                _log.error("Snapmirror Failover operation failed");
                ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror Failover operation failed:");
                return BiosCommandResult.createErrorResult(error);
            }

        } catch (Exception e) {
            _log.error("Snapmirror Failover failed", e);
            ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror Failover failed:" + e.getMessage());
            return BiosCommandResult.createErrorResult(error);

        }
        return BiosCommandResult.createSuccessfulResult();
    }

    /**
     * Disables future transfers to a SnapMirror destination
     * and api should be issue on destination server
     * 
     * @param sourceSystem
     * @param targetSystem
     * @param sourceFs
     * @param targetFs
     * @param taskCompleter
     * @return
     */
    BiosCommandResult doQuiesceSnapMirror(StorageSystem sourceSystem, StorageSystem targetSystem, FileShare sourceFs, FileShare targetFs,
            TaskCompleter taskCompleter) {
        SnapmirrorInfo snapMirrorInfo = prepareSnapMirrorInfo(sourceSystem, targetSystem, sourceFs, targetFs);

        NetAppClusterApi ncApi = getNetAppcClient(targetSystem, targetFs);

        try {
            SnapmirrorInfoResp mirrorInfoResp = ncApi.getSnapMirrorInfo(snapMirrorInfo);

            if (SnapmirrorState.READY.equals(mirrorInfoResp.getMirrorState()) ||
                    SnapmirrorState.SOURCE.equals(mirrorInfoResp.getMirrorState())) {
                ncApi.quienceSnapMirror(snapMirrorInfo);

                NetAppCSnapMirrorJob netappCSnapMirrorJob = new NetAppCSnapMirrorJob(snapMirrorInfo, targetSystem.getId(), taskCompleter,
                        SnapmirrorState.PAUSED.toString());
                ControllerServiceImpl.enqueueJob(new QueueJob(netappCSnapMirrorJob));
                return BiosCommandResult.createPendingResult();
            } else {
                ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror Quiesce operation failed and mirror state :"
                        + mirrorInfoResp.getMirrorState());
                return BiosCommandResult.createErrorResult(error);
            }
        } catch (Exception e) {
            _log.error("Snapmirror Quiesce operation failed", e);
            ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror Quiesce operation failed:" + e.getMessage());
            return BiosCommandResult.createErrorResult(error);

        }
    }

    /**
     * Enables future transfers for a SnapMirror relationship that has been quiesced
     * 
     * @param sourceSystem
     * @param targetSystem
     * @param sourceFs
     * @param targetFs
     * @param taskCompleter
     * @return
     */
    BiosCommandResult doResumeSnapMirror(StorageSystem sourceSystem, StorageSystem targetSystem, FileShare sourceFs, FileShare targetFs,
            TaskCompleter taskCompleter) {
        SnapmirrorInfo snapMirrorInfo = prepareSnapMirrorInfo(sourceSystem, targetSystem, sourceFs, targetFs);

        NetAppClusterApi ncApi = getNetAppcClient(targetSystem, targetFs);

        try {
            SnapmirrorInfoResp mirrorInfoResp = ncApi.getSnapMirrorInfo(snapMirrorInfo);
            if (SnapmirrorState.PAUSED.equals(mirrorInfoResp.getMirrorState()) ||
                    SnapmirrorState.SOURCE.equals(mirrorInfoResp.getMirrorState())) {
                ncApi.resumeSnapMirror(snapMirrorInfo);

                NetAppCSnapMirrorJob netappCSnapMirrorJob = new NetAppCSnapMirrorJob(snapMirrorInfo, targetSystem.getId(), taskCompleter,
                        SnapmirrorState.SYNCRONIZED.toString());

                ControllerServiceImpl.enqueueJob(new QueueJob(netappCSnapMirrorJob));
                return BiosCommandResult.createPendingResult();
            } else {
                ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror Resume operation failed and mirror state :"
                        + mirrorInfoResp.getMirrorState());
                return BiosCommandResult.createErrorResult(error);
            }

        } catch (Exception e) {
            _log.error("Snapmirror Resume operation failed", e);
            ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror Resume operation failed:" + e.getMessage());
            return BiosCommandResult.createErrorResult(error);

        }

    }

    /**
     * The snapmirror-release API removes a SnapMirror relationship on the source endpoint
     * 
     * @param sourceSystem
     * @param targetSystem
     * @param sourceFs
     * @param targetFs
     * @param taskCompleter
     * @return
     */
    BiosCommandResult doReleaseSnapMirror(StorageSystem sourceSystem, StorageSystem targetSystem, FileShare sourceFs,
            FileShare targetFs,
            TaskCompleter taskCompleter) {
        SnapmirrorInfo snapMirrorInfo = prepareSnapMirrorInfo(sourceSystem, targetSystem, sourceFs, targetFs);

        NetAppClusterApi ncApi = getNetAppcClient(targetSystem, targetFs);
        try {
            SnapMirrorVolumeStatus mirrorVolumeStatus = ncApi.getSnapMirrorVolumeStatus(sourceFs.getName());

            if (mirrorVolumeStatus != null && false == mirrorVolumeStatus.isTransferInProgress()) {
                ncApi.abortSnapMirror(snapMirrorInfo);

                NetAppSnapMirrorReleaseJob snapMirrorReleaseJob = new NetAppSnapMirrorReleaseJob(snapMirrorInfo.getDestinationLocation(),
                        sourceSystem.getId(),
                        taskCompleter);

                ControllerServiceImpl.enqueueJob(new QueueJob(snapMirrorReleaseJob));
                return BiosCommandResult.createPendingResult();
            } else {
                return BiosCommandResult.createSuccessfulResult();
            }

        } catch (Exception e) {
            _log.error("Snapmirror Release operation failed", e);
            ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror Release operation failed:" + e.getMessage());
            return BiosCommandResult.createErrorResult(error);
        }

    }

    BiosCommandResult doDestroySnapMirror(StorageSystem sourceSystem, StorageSystem targetSystem, FileShare sourceFs,
            FileShare targetFs,
            TaskCompleter taskCompleter) {
        SnapmirrorInfo snapMirrorInfo = prepareSnapMirrorInfo(sourceSystem, targetSystem, sourceFs, targetFs);

        _log.info("NetAppCMirrorOperations - doDestroySnapMirror sourcelocation {} and destinationlocation {}- start",
                snapMirrorInfo.getSourceLocation(), snapMirrorInfo.getDestinationLocation());
        NetAppClusterApi ncApi = getNetAppcClient(targetSystem, targetFs);
        SnapmirrorInfoResp mirrorInfoResp = ncApi.getSnapMirrorInfo(snapMirrorInfo);

        // set relationship id
        snapMirrorInfo.setRelationshipId(mirrorInfoResp.getRelationshipId());

        try {
            boolean isDestorySnapMirror = ncApi.destorySnapMirror(snapMirrorInfo);
            if (isDestorySnapMirror == true) {
                ncApi.releaseSnapMirror(snapMirrorInfo);
                NetAppSnapMirrorDestroyJob netappCSnapMirrorJob = new NetAppSnapMirrorDestroyJob(mirrorInfoResp.getRelationshipId(),
                        sourceSystem.getId(), taskCompleter);

                ControllerServiceImpl.enqueueJob(new QueueJob(netappCSnapMirrorJob));
                return BiosCommandResult.createPendingResult();
            }

        } catch (Exception e) {
            _log.error("Snapmirror destory operation failed", e);
            ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror destory operation failed:" + e.getMessage());
            return BiosCommandResult.createErrorResult(error);

        }

        return BiosCommandResult.createSuccessfulResult();
    }

    /**
     * Re-establishes a mirroring relationship between a source volume and a destination volume
     * 
     * @param sourceSystem
     * @param targetSystem
     * @param sourceFs
     * @param targetFs
     * @param taskCompleter
     * @return
     */
    BiosCommandResult doResyncSnapMirror(StorageSystem sourceSystem, StorageSystem targetSystem, FileShare sourceFs, FileShare targetFs,
            TaskCompleter taskCompleter) {
        SnapmirrorInfo snapMirrorInfo = prepareSnapMirrorInfo(sourceSystem, targetSystem, sourceFs, targetFs);

        String sourceVserver = findSVMName(sourceFs);
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(sourceSystem.getIpAddress(),
                sourceSystem.getPortNumber(), sourceSystem.getUsername(),
                sourceSystem.getPassword()).https(true).svm(sourceVserver).build();
        try {
            SnapMirrorVolumeStatus mirrorVolumeStatus = ncApi.getSnapMirrorVolumeStatus(sourceFs.getName());

            if (mirrorVolumeStatus != null && false == mirrorVolumeStatus.isTransferInProgress()) {
                SnapmirrorResp snapMirrorResult = ncApi.resyncSnapMirror(snapMirrorInfo);
                if (snapMirrorResult != null) {
                    if (SnapmirrorResp.INPROGRESS.equals(snapMirrorResult.getResultStatus())) {
                        NetAppCSnapMirrorJob netappCSnapMirrorJob = null;
                        ControllerServiceImpl.enqueueJob(new QueueJob(netappCSnapMirrorJob));
                        return BiosCommandResult.createPendingResult();

                    } else if (SnapmirrorResp.FAILED.equals(snapMirrorResult.getResultStatus())) {
                        ServiceError error = DeviceControllerErrors.netappc
                                .jobFailed("Snapmirror Resync operation failed and error: "
                                        + snapMirrorResult.getResultErrorMessage());
                        error.setCode(snapMirrorResult.getResultErrorCode());
                        return BiosCommandResult.createErrorResult(error);

                    } else {
                        return BiosCommandResult.createSuccessfulResult();
                    }
                }
            } else {
                ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror Resync operation failed and is mirror break :"
                        + "false");
                return BiosCommandResult.createErrorResult(error);
            }
        } catch (Exception e) {
            _log.error("Snapmirror Resync operation failed", e);
            ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror Resync operation failed:" + e.getMessage());
            return BiosCommandResult.createErrorResult(error);

        }

        return BiosCommandResult.createSuccessfulResult();
    }

    BiosCommandResult doAbortSnapMirror(StorageSystem sourceSystem, StorageSystem targetSystem, FileShare sourceFs, FileShare targetFs,
            TaskCompleter taskCompleter) {
        SnapmirrorInfo snapMirrorInfo = prepareSnapMirrorInfo(sourceSystem, targetSystem, sourceFs, targetFs);
        String destVserver = findSVMName(targetFs);
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(targetSystem.getIpAddress(),
                targetSystem.getPortNumber(), targetSystem.getUsername(),
                targetSystem.getPassword()).https(true).svm(destVserver).build();

        try {
            SnapMirrorVolumeStatus mirrorVolumeStatus = ncApi.getSnapMirrorVolumeStatus(sourceFs.getName());

            if (mirrorVolumeStatus != null && false == mirrorVolumeStatus.isTransferInProgress()) {
                ncApi.abortSnapMirror(snapMirrorInfo);

                NetAppSnapMirrorAbortJob snapMirrorQuiesceJob = new NetAppSnapMirrorAbortJob(snapMirrorInfo.getDestinationLocation(),
                        sourceSystem.getId(),
                        taskCompleter);

                ControllerServiceImpl.enqueueJob(new QueueJob(snapMirrorQuiesceJob));
                return BiosCommandResult.createPendingResult();
            } else {
                return BiosCommandResult.createSuccessfulResult();
            }

        } catch (Exception e) {
            _log.error("Snapmirror Quiesce operation failed", e);
            ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror Quiesce operation failed:" + e.getMessage());
            return BiosCommandResult.createErrorResult(error);

        }
    }

    // // cron schedule operations

    /**
     * Create a new cron job schedule entry
     * 
     * @param clusterApi
     * @param fsRpoValue
     * @param fsRpoType
     * @param jobName
     * @return
     */
    private SnapmirrorCronScheduleInfo
            doCreateCronSchedule(NetAppClusterApi clusterApi, String fsRpoValue, String fsRpoType, String jobName) {

        return clusterApi.createCronSchedule(fsRpoValue, fsRpoType, jobName);
    }

    /**
     * Create a new cron job schedule entry
     * 
     * @param storage
     * @param share
     * @param fsRpoValue
     * @param fsRpoType
     * @param jobName
     * @return
     */
    private SnapmirrorCronScheduleInfo
            doCreateCronSchedule(StorageSystem storage, FileShare share, String fsRpoValue, String fsRpoType, String jobName) {
        String portGroup = findSVMName(share);
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).svm(portGroup).build();

        return ncApi.createCronSchedule(fsRpoValue, fsRpoType, jobName);
    }

    private SnapmirrorCronScheduleInfo
            doModifyCronSchedule(NetAppClusterApi clusterApi, String fsRpoValue, String fsRpoType, String jobName) {
        return clusterApi.modifyCronSchedule(fsRpoValue, fsRpoType, jobName);
    }

    /**
     * Delete a single cron job schedule entry. The entry must not be in use.
     * 
     * @param clusterApi
     * @param jobName
     * @return
     */
    private boolean doDeleteCronSchedule(NetAppClusterApi clusterApi, String jobName) {
        return clusterApi.deleteCronSchedule(jobName);
    }

    private boolean doDeleteCronSchedule(StorageSystem storage, FileShare share, String jobName) {
        NetAppClusterApi ncApi = getNetAppcClient(storage, share);
        return ncApi.deleteCronSchedule(jobName);
    }

    // helper functions
    /**
     * get netappc client
     * 
     * @param storage
     * @param fs
     * @return
     */
    NetAppClusterApi getNetAppcClient(final StorageSystem storage, final FileShare fs) {
        String vserver = findSVMName(fs);
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).svm(vserver).build();
        return ncApi;
    }

    void updateTaskStatus(final BiosCommandResult cmdResult, TaskCompleter completer) {
        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else if (cmdResult.getCommandPending()) {
            completer.statusPending(_dbClient, cmdResult.getMessage());
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }
    }

    /**
     * Build SnapmirrorInfo object
     * 
     * @param sourceCluster
     * @param targetCluster
     * @param sourceFs
     * @param targetFs
     * @return
     */
    SnapmirrorInfo prepareSnapMirrorInfo(StorageSystem sourceCluster, StorageSystem targetCluster,
            FileShare sourceFs, FileShare targetFs) {
        SnapmirrorInfo mirrorInfo = new SnapmirrorInfo();

        // source cluster
        mirrorInfo.setSourceVolume(sourceFs.getName());

        String sourceVserver = findSVMName(sourceFs);
        mirrorInfo.setSourceVserver(sourceVserver);

        String sourceLocation = getLocation(sourceFs);
        mirrorInfo.setSourceLocation(sourceLocation);

        // destination cluster
        mirrorInfo.setDestinationVolume(targetFs.getName());

        String destinationVserver = findSVMName(targetFs);
        mirrorInfo.setDestinationVserver(destinationVserver);

        String destinationLocation = getLocation(targetFs);
        mirrorInfo.setDestinationLocation(destinationLocation);

        return mirrorInfo;
    }

    public String getLocation(FileShare share) {
        StringBuilder builderLoc = new StringBuilder();
        String portGroup = findSVMName(share);
        // vserver
        builderLoc.append(portGroup);
        builderLoc.append(":");

        // volume
        builderLoc.append(share.getName());
        return builderLoc.toString();
    }

    /**
     * Return the svm name associated with the file system. If a svm is not associated with
     * this file system, then it will return null.
     */
    private String findSVMName(FileShare fs) {
        String portGroup = null;

        URI port = fs.getStoragePort();
        if (port == null) {
            _log.info("No storage port URI to retrieve svm name");
        } else {
            StoragePort stPort = _dbClient.queryObject(StoragePort.class, port);
            if (stPort != null) {
                URI haDomainUri = stPort.getStorageHADomain();
                if (haDomainUri == null) {
                    _log.info("No Port Group URI for port {}", port);
                } else {
                    StorageHADomain haDomain = _dbClient.queryObject(StorageHADomain.class, haDomainUri);
                    if (haDomain != null && haDomain.getVirtual() == true) {
                        portGroup = stPort.getPortGroup();
                        _log.debug("using port {} and svm {}", stPort.getPortNetworkId(), portGroup);
                    }
                }
            }
        }
        return portGroup;
    }

}
