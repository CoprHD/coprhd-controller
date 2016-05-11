/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.netappc;

import java.net.URI;
import java.util.Map;

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
import com.emc.storageos.volumecontroller.impl.netappc.job.NetAppSnapMirrorCreateJob;
import com.emc.storageos.volumecontroller.impl.netappc.job.NetAppSnapMirrorQuiesceJob;
import com.emc.storageos.volumecontroller.impl.netappc.job.NetAppSnapMirrorResumeJob;
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

        BiosCommandResult cmdResult = doCreateSnapMirror(sourceSystem, targetSystem, sourceFs, targetFs, completer);

        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else if (cmdResult.getCommandPending()) {
            completer.statusPending(_dbClient, cmdResult.getMessage());
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }

    }

    @Override
    public void stopMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void startMirrorFileShareLink(StorageSystem sourceSystem, FileShare targetFs, TaskCompleter completer, String policyName)
            throws DeviceControllerException {
        _log.info("NetAppCMirrorOperations -  startMirrorFileShareLink started ");

        FileShare sourceFs = _dbClient.queryObject(FileShare.class, targetFs.getParentFileShare().getURI());
        StorageSystem targetSystem = _dbClient.queryObject(StorageSystem.class, targetFs.getStorageDevice());

        BiosCommandResult cmdResult = doInitialiseSnapMirror(sourceSystem, targetSystem, sourceFs, targetFs, completer);

        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else if (cmdResult.getCommandPending()) {
            completer.statusPending(_dbClient, cmdResult.getMessage());
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }

        _log.info("NetappCMirrorFileOperations -  startMirrorFileShareLink source file {} and dest file {} - complete ",
                sourceFs.getName(), targetFs.getName());

    }

    @Override
    public void failoverMirrorFileShareLink(StorageSystem targetSystem, FileShare targetFs, TaskCompleter taskCompleter, String policyName)
            throws DeviceControllerException {
        BiosCommandResult cmdResult = null;
        FileShare sourceFs = _dbClient.queryObject(FileShare.class, targetFs.getParentFileShare().getURI());
        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceFs.getStorageDevice());

        cmdResult = doFailoverSnapMirror(sourceSystem, targetSystem, sourceFs, targetFs, taskCompleter);
        if (cmdResult.getCommandSuccess()) {
            taskCompleter.ready(_dbClient);
        } else if (cmdResult.getCommandPending()) {
            taskCompleter.statusPending(_dbClient, cmdResult.getMessage());
        }
        else {
            taskCompleter.error(_dbClient, cmdResult.getServiceCoded());
        }

    }

    @Override
    public void deleteMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {
    }

    @Override
    public void pauseMirrorFileShareLink(StorageSystem targetSystem, FileShare targetFs, TaskCompleter completer)
            throws DeviceControllerException {

        FileShare sourceFs = _dbClient.queryObject(FileShare.class, targetFs.getParentFileShare().getURI());
        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceFs.getStorageDevice());

        BiosCommandResult cmdResult =
                doQuiesceSnapMirror(sourceSystem, targetSystem, sourceFs, targetFs, completer);

        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else if (cmdResult.getCommandPending()) {
            completer.statusPending(_dbClient, cmdResult.getMessage());
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }
    }

    @Override
    public void resumeMirrorFileShareLink(StorageSystem targetSystem, FileShare targetFs, TaskCompleter completer)
            throws DeviceControllerException {
        FileShare sourceFs = _dbClient.queryObject(FileShare.class, targetFs.getParentFileShare().getURI());
        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class, sourceFs.getStorageDevice());

        BiosCommandResult cmdResult =
                doQuiesceSnapMirror(sourceSystem, targetSystem, sourceFs, targetFs, completer);

        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else if (cmdResult.getCommandPending()) {
            completer.statusPending(_dbClient, cmdResult.getMessage());
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }
    }

    @Override
    public void resyncMirrorFileShareLink(StorageSystem primarySystem, StorageSystem secondarySystem, FileShare target,
            TaskCompleter completer, String policyName) {

    }

    @Override
    public void cancelMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {

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

    BiosCommandResult
            doInitialiseSnapMirror(StorageSystem sourceSystem, StorageSystem targetSystem, FileShare sourceFs, FileShare targetFs,
                    TaskCompleter taskCompleter) {
        SnapmirrorInfo snapMirrorInfo = prepareSnapMirrorInfo(sourceSystem, targetSystem, sourceFs, targetFs);

        String sourceVserver = findSVMName(sourceFs);
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(sourceSystem.getIpAddress(),
                sourceSystem.getPortNumber(), sourceSystem.getUsername(),
                sourceSystem.getPassword()).https(true).svm(sourceVserver).build();
        try {
            SnapmirrorInfoResp mirrorInfoResp = ncApi.getSnapMirrorInfo(snapMirrorInfo);
            if (SnapmirrorState.UNKNOWN.equals(mirrorInfoResp.getMirrorState()) ||
                    SnapmirrorState.READY.equals(mirrorInfoResp.getMirrorState())) {

                SnapmirrorResp snapMirrorResult = ncApi.initialiseSnapMirror(snapMirrorInfo);

                if (SnapmirrorResp.INPROGRESS.equals(snapMirrorResult.getResultStatus())) {
                    NetAppCSnapMirrorJob netappCSnapMirrorJob = new NetAppCSnapMirrorJob(snapMirrorResult.getResultJobid().toString(),
                            sourceSystem.getId(), taskCompleter);

                    ControllerServiceImpl.enqueueJob(new QueueJob(netappCSnapMirrorJob));
                    return BiosCommandResult.createPendingResult();

                } else if (SnapmirrorResp.FAILED.equals(snapMirrorResult.getResultStatus())) {
                    ServiceError error = DeviceControllerErrors.netappc
                            .jobFailed("Snapmirror start operation failed and error: "
                                    + snapMirrorResult.getResultErrorMessage());
                    error.setCode(snapMirrorResult.getResultErrorCode());
                    return BiosCommandResult.createErrorResult(error);

                } else {
                    return BiosCommandResult.createSuccessfulResult();
                }
            } else {
                _log.error("Snapmirror start failed");
                ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror start operation failed:");
                return BiosCommandResult.createErrorResult(error);

            }
        } catch (Exception e) {
            _log.error("Snapmirror start failed", e);
            ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror start failed:" + e.getMessage());
            return BiosCommandResult.createErrorResult(error);

        }
    }

    BiosCommandResult doCreateSnapMirror(StorageSystem sourceSystem, StorageSystem targetSystem, FileShare sourceFs, FileShare targetFs,
            TaskCompleter taskCompleter) {
        SnapmirrorInfo snapMirrorInfo = prepareSnapMirrorInfo(sourceSystem, targetSystem, sourceFs, targetFs);
        String sourceVserver = findSVMName(sourceFs);
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(sourceSystem.getIpAddress(),
                sourceSystem.getPortNumber(), sourceSystem.getUsername(),
                sourceSystem.getPassword()).https(true).svm(sourceVserver).build();
        try {
            SnapmirrorInfoResp snapMirrorResult = ncApi.createSnapMirror(snapMirrorInfo);

            if (snapMirrorResult.getMirrorState().equals(SnapmirrorState.READY) ||
                    snapMirrorResult.getMirrorState().equals(SnapmirrorState.SOURCE)) {
                NetAppSnapMirrorCreateJob snapMirrorCreateJob = new NetAppSnapMirrorCreateJob(snapMirrorInfo.getDestinationLocation(),
                        sourceSystem.getId(),
                        taskCompleter);

                ControllerServiceImpl.enqueueJob(new QueueJob(snapMirrorCreateJob));
                return BiosCommandResult.createPendingResult();
            } else {
                ServiceError error =
                        DeviceControllerErrors.netappc.jobFailed("Snapmirror create failed");
                return BiosCommandResult.createErrorResult(error);
            }

        } catch (Exception e) {
            _log.error("Snapmirror create failed", e);
            ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror create failed:" + e.getMessage());
            return BiosCommandResult.createErrorResult(error);
        }
    }

    BiosCommandResult doFailoverSnapMirror(StorageSystem sourceSystem, StorageSystem targetSystem, FileShare sourceFs, FileShare targetFs,
            TaskCompleter taskCompleter) {
        SnapmirrorInfo snapMirrorInfo = prepareSnapMirrorInfo(sourceSystem, targetSystem, sourceFs, targetFs);
        String destVserver = findSVMName(targetFs);
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(targetSystem.getIpAddress(),
                targetSystem.getPortNumber(), targetSystem.getUsername(),
                targetSystem.getPassword()).https(true).svm(destVserver).build();

        try {
            SnapmirrorInfoResp mirrorInfoResp = ncApi.getSnapMirrorInfo(snapMirrorInfo);

            if (SnapmirrorState.PAUSED.equals(mirrorInfoResp.getMirrorState()) ||
                    SnapmirrorState.SYNCRONIZED.equals(mirrorInfoResp.getMirrorState())) {
                SnapmirrorResp snapMirrorResult = ncApi.breakSnapMirrorAsync(snapMirrorInfo);
                if (snapMirrorResult != null) {
                    if (SnapmirrorResp.INPROGRESS.equals(snapMirrorResult.getResultStatus())) {
                        NetAppCSnapMirrorJob netappCSnapMirrorJob = new NetAppCSnapMirrorJob(snapMirrorResult.getResultJobid().toString(),
                                sourceSystem.getId(), taskCompleter);

                        ControllerServiceImpl.enqueueJob(new QueueJob(netappCSnapMirrorJob));
                        return BiosCommandResult.createPendingResult();

                    } else if (SnapmirrorResp.FAILED.equals(snapMirrorResult.getResultStatus())) {
                        ServiceError error = DeviceControllerErrors.netappc
                                .jobFailed("Snapmirror break operation failed and error: "
                                        + snapMirrorResult.getResultErrorMessage());
                        error.setCode(snapMirrorResult.getResultErrorCode());
                        return BiosCommandResult.createErrorResult(error);

                    } else {
                        return BiosCommandResult.createSuccessfulResult();
                    }
                }
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

    BiosCommandResult doDeleteSnapMirror(StorageSystem sourceSystem, StorageSystem targetSystem, FileShare sourceFs, FileShare targetFs,
            TaskCompleter taskCompleter) {
        return BiosCommandResult.createSuccessfulResult();
    }

    BiosCommandResult doResyncSnapMirror(StorageSystem sourceSystem, StorageSystem targetSystem, FileShare sourceFs, FileShare targetFs,
            TaskCompleter taskCompleter) {
        SnapmirrorInfo snapMirrorInfo = prepareSnapMirrorInfo(sourceSystem, targetSystem, sourceFs, targetFs);

        String sourceVserver = findSVMName(sourceFs);
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(sourceSystem.getIpAddress(),
                sourceSystem.getPortNumber(), sourceSystem.getUsername(),
                sourceSystem.getPassword()).https(true).svm(sourceVserver).build();
        try {
            SnapmirrorInfoResp mirrorInfoResp = ncApi.getSnapMirrorInfo(snapMirrorInfo);

            if (SnapmirrorState.FAILOVER.equals(mirrorInfoResp.getMirrorState()) ||
                    SnapmirrorState.SOURCE.equals(mirrorInfoResp.getMirrorState())) {
                SnapmirrorResp snapMirrorResult = ncApi.resyncSnapMirror(snapMirrorInfo);
                if (snapMirrorResult != null) {
                    if (SnapmirrorResp.INPROGRESS.equals(snapMirrorResult.getResultStatus())) {
                        NetAppCSnapMirrorJob netappCSnapMirrorJob = new NetAppCSnapMirrorJob(snapMirrorResult.getResultJobid().toString(),
                                sourceSystem.getId(), taskCompleter);

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
                ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror Resync operation failed and mirror state :"
                        + mirrorInfoResp.getMirrorState());
                return BiosCommandResult.createErrorResult(error);
            }
        } catch (Exception e) {
            _log.error("Snapmirror Resync operation failed", e);
            ServiceError error = DeviceControllerErrors.netappc.jobFailed("Snapmirror Resync operation failed:" + e.getMessage());
            return BiosCommandResult.createErrorResult(error);

        }

        return BiosCommandResult.createSuccessfulResult();
    }

    BiosCommandResult doQuiesceSnapMirror(StorageSystem sourceSystem, StorageSystem targetSystem, FileShare sourceFs, FileShare targetFs,
            TaskCompleter taskCompleter) {
        SnapmirrorInfo snapMirrorInfo = prepareSnapMirrorInfo(sourceSystem, targetSystem, sourceFs, targetFs);

        String sourceVserver = findSVMName(sourceFs);
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(sourceSystem.getIpAddress(),
                sourceSystem.getPortNumber(), sourceSystem.getUsername(),
                sourceSystem.getPassword()).https(true).svm(sourceVserver).build();

        try {
            SnapmirrorInfoResp mirrorInfoResp = ncApi.getSnapMirrorInfo(snapMirrorInfo);

            if (SnapmirrorState.READY.equals(mirrorInfoResp.getMirrorState()) ||
                    SnapmirrorState.SOURCE.equals(mirrorInfoResp.getMirrorState())) {
                ncApi.quienceSnapMirror(snapMirrorInfo);

                NetAppSnapMirrorQuiesceJob snapMirrorQuiesceJob = new NetAppSnapMirrorQuiesceJob(snapMirrorInfo.getDestinationLocation(),
                        sourceSystem.getId(),
                        taskCompleter);

                ControllerServiceImpl.enqueueJob(new QueueJob(snapMirrorQuiesceJob));
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

    BiosCommandResult doResumeSnapMirror(StorageSystem sourceSystem, StorageSystem targetSystem, FileShare sourceFs, FileShare targetFs,
            TaskCompleter taskCompleter) {
        SnapmirrorInfo snapMirrorInfo = prepareSnapMirrorInfo(sourceSystem, targetSystem, sourceFs, targetFs);

        String sourceVserver = findSVMName(sourceFs);
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(sourceSystem.getIpAddress(),
                sourceSystem.getPortNumber(), sourceSystem.getUsername(),
                sourceSystem.getPassword()).https(true).svm(sourceVserver).build();

        try {
            SnapmirrorInfoResp mirrorInfoResp = ncApi.getSnapMirrorInfo(snapMirrorInfo);
            if (SnapmirrorState.PAUSED.equals(mirrorInfoResp.getMirrorState()) ||
                    SnapmirrorState.SOURCE.equals(mirrorInfoResp.getMirrorState())) {
                ncApi.quienceSnapMirror(snapMirrorInfo);

                NetAppSnapMirrorResumeJob snapMirrorResumeJob = new NetAppSnapMirrorResumeJob(snapMirrorInfo.getDestinationLocation(),
                        sourceSystem.getId(),
                        taskCompleter);

                ControllerServiceImpl.enqueueJob(new QueueJob(snapMirrorResumeJob));
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

    SnapmirrorInfo prepareSnapMirrorInfo(StorageSystem sourceCluster, StorageSystem targetCluster,
            FileShare sourceFs, FileShare targetFs) {
        SnapmirrorInfo mirrorInfo = new SnapmirrorInfo();

        // source cluster
        mirrorInfo.setSourceVolume(sourceFs.getName());

        String sourceVserver = findSVMName(sourceFs);
        mirrorInfo.setSourceVserver(sourceVserver);

        String sourceLocation = getLocation(sourceCluster, sourceFs);
        mirrorInfo.setSourceLocation(sourceLocation);

        // destination cluster
        mirrorInfo.setDestinationVolume(targetFs.getName());

        String destinationVserver = findSVMName(targetFs);
        mirrorInfo.setDestinationVserver(destinationVserver);

        String destinationLocation = getLocation(targetCluster, sourceFs);
        mirrorInfo.setDestinationLocation(destinationLocation);

        return mirrorInfo;
    }

    public String getLocation(NetAppClusterApi nApi, FileShare share) {
        StringBuilder builderLoc = new StringBuilder();

        Map<String, String> systeminfo = nApi.systemInfo();
        String systemName = systeminfo.get("system-name");

        String portGroup = findSVMName(share);

        // [<cluster>:]//<vserver>/<volume>

        // cluster
        builderLoc.append(systemName);
        builderLoc.append("://");

        // vserver
        builderLoc.append(portGroup);
        builderLoc.append("/");

        // volume
        builderLoc.append(share.getName());
        return builderLoc.toString();
    }

    public String getLocation(StorageSystem storage, FileShare share) {
        String portGroup = findSVMName(share);
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).svm(portGroup).build();

        return getLocation(ncApi, share);

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
