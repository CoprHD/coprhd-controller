package com.emc.storageos.volumecontroller.impl.netapp;

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
import com.emc.storageos.netapp.NetAppApi;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.file.FileMirrorOperations;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.netapp.job.NetAppSnapMirrorCreateJob;
import com.emc.storageos.volumecontroller.impl.netapp.job.NetAppSnapMirrorFailover;
import com.emc.storageos.volumecontroller.impl.netapp.job.NetAppSnapMirrorQuiesceJob;
import com.emc.storageos.volumecontroller.impl.netapp.job.NetAppSnapMirrorStartJob;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.iwave.ext.netapp.model.SnapMirrorState;
import com.iwave.ext.netapp.model.SnapMirrorStatusInfo;

public class NetappMirrorFileOperations implements FileMirrorOperations {
    private static final Logger _log = LoggerFactory
            .getLogger(NetappMirrorFileOperations.class);

    private DbClient _dbClient;

    public void setDbClient(DbClient dbClient) {
        this._dbClient = dbClient;
    }

    public NetappMirrorFileOperations() {

    }

    @Override
    public void createMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {
        _log.info("NetappMirrorFileOperations -  createMirrorFileShareLink started ");

        FileShare sourceFileShare = _dbClient.queryObject(FileShare.class, source);
        FileShare targetFileShare = _dbClient.queryObject(FileShare.class, target);

        StorageSystem sourceStorageSystem = _dbClient.queryObject(StorageSystem.class, sourceFileShare.getStorageDevice());
        StorageSystem targetStorageSystem = _dbClient.queryObject(StorageSystem.class, targetFileShare.getStorageDevice());

        VirtualPool virtualPool = _dbClient.queryObject(VirtualPool.class, sourceFileShare.getVirtualPool());
        BiosCommandResult cmdResult = null;
        if (virtualPool != null && virtualPool.getFrRpoValue() > 0) {
            cmdResult = setScheduleSnapMirror(sourceStorageSystem, targetStorageSystem,
                    sourceFileShare, targetFileShare, completer);
        }

        if (cmdResult == null) {
            completer.ready(_dbClient);
            return;
        }

        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else if (cmdResult.getCommandPending()) {
            completer.statusPending(_dbClient, cmdResult.getMessage());
        }
        else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }

        _log.info("NetappMirrorFileOperations -  createMirrorFileShareLink source file {} and dest file {} - complete ",
                sourceFileShare.getName(), targetFileShare.getName());
    }

    @Override
    public void stopMirrorFileShareLink(StorageSystem sourceStorage, FileShare targetFs, TaskCompleter completer)
            throws DeviceControllerException {

        _log.info("NetappMirrorFileOperations -  stopMirrorFileShareLink started. Calling deleteMirrorFileShareLink.");
        this.deleteMirrorFileShareLink(sourceStorage, targetFs.getParentFileShare().getURI(), targetFs.getId(), completer);
        WorkflowStepCompleter.stepSucceded(completer.getOpId());

        _log.info("NetappMirrorFileOperations -  stopMirrorFileShareLink finished.");
    }

    @Override
    public void
            startMirrorFileShareLink(StorageSystem sourceStorage, FileShare targetFileShare, TaskCompleter completer, String policyName)
                    throws DeviceControllerException {
        _log.info("NetappMirrorFileOperations -  startMirrorFileShareLink started ");

        FileShare sourceFileShare = _dbClient.queryObject(FileShare.class, targetFileShare.getParentFileShare().getURI());
        StorageSystem targetStorageSystem = _dbClient.queryObject(StorageSystem.class, targetFileShare.getStorageDevice());

        BiosCommandResult cmdResult = doInitializeSnapMirror(sourceStorage, targetStorageSystem,
                sourceFileShare, targetFileShare, completer);

        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else if (cmdResult.getCommandPending()) {
            completer.statusPending(_dbClient, cmdResult.getMessage());
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }

        _log.info("NetappMirrorFileOperations -  startMirrorFileShareLink source file {} and dest file {} - complete ",
                sourceFileShare.getName(), targetFileShare.getName());
    }

    @Override
    public void pauseMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {

        _log.info("Calling NetappMirrorFileOperations - pauseMirrorFileShareLink on destination {} - start ",
                target.getName());

        StorageSystem targetStorageSystem = _dbClient.queryObject(StorageSystem.class, target.getStorageDevice());

        BiosCommandResult cmdResult = doPauseSnapMirrorSync(targetStorageSystem, target, completer);

        if (cmdResult.getCommandSuccess()) {

            completer.ready(_dbClient);
        } else if (cmdResult.getCommandPending()) {
            completer.statusPending(_dbClient, cmdResult.getMessage());
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }

        _log.info("NetappMirrorFileOperations - pauseMirrorFileShareLink on destination {} - complete ",
                target.getName());

    }

    @Override
    public void resumeMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {

        StorageSystem targetStorageSystem = _dbClient.queryObject(StorageSystem.class, target.getStorageDevice());
        _log.info("Calling NetappMirrorFileOperations - resumeMirrorFileShareLink on destination {} - start",
                target.getName());
        BiosCommandResult cmdResult = doResumeSnapMirror(targetStorageSystem, target, completer);

        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }

        _log.info("NetappMirrorFileOperations - resumeMirrorFileShareLink on destination {} - complete ",
                target.getName());

    }

    @Override
    public void failoverMirrorFileShareLink(StorageSystem targetSystem, FileShare targetFileShare, TaskCompleter completer,
            String policyName)
            throws DeviceControllerException {

        BiosCommandResult cmdResult = doFailoverSnapMirror(targetSystem, targetFileShare, completer);

        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else if (cmdResult.getCommandPending()) {
            completer.statusPending(_dbClient, cmdResult.getMessage());
        }
        else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }

    }

    @Override
    public void resyncMirrorFileShareLink(StorageSystem primarySystem, StorageSystem secondarySystem, FileShare fileshare,
            TaskCompleter completer, String policyName) {
        FileShare source = null;
        FileShare target = null;
        if (fileshare.getParentFileShare() == null) {
            source = fileshare;
            // we have fix this target issue
        } else {
            source = _dbClient.queryObject(FileShare.class, fileshare.getParentFileShare().getURI());
            target = fileshare;
        }

        String portGroup = findVfilerName(fileshare);
        BiosCommandResult cmdResult = doResyncSnapMirror(primarySystem, portGroup, source.getMountPath(), target.getPath(), completer);

        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }
    }

    @Override
    public void deleteMirrorFileShareLink(StorageSystem sourceSystem, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {

        FileShare sourceFileShare = _dbClient.queryObject(FileShare.class, source);
        FileShare targetFileShare = _dbClient.queryObject(FileShare.class, target);
        StorageSystem targetStorage = _dbClient.queryObject(StorageSystem.class, targetFileShare.getStorageDevice());

        BiosCommandResult cmdResult = null;

        _log.info("Calling snapmirror pause.");
        cmdResult = doPauseSnapMirror(targetStorage, targetFileShare, completer);
        if (cmdResult.getCommandSuccess()) {
            // Call snapmirror break
            _log.info("Calling snapmirror break.");
            cmdResult = doFailoverSnapMirror(targetStorage, targetFileShare, completer);
            if (cmdResult.getCommandSuccess()) {
                _log.info("Calling snapmirror release.");
                cmdResult = doReleaseSnapMirror(sourceSystem, targetStorage,
                        sourceFileShare, targetFileShare, completer);
                if (cmdResult.getCommandSuccess()) {
                    _log.info("Calling snapmirror delete schedule.");
                    cmdResult = deleteSnapMirrorSchedule(sourceSystem, targetStorage,
                            sourceFileShare, targetFileShare, completer);
                    if (cmdResult.getCommandSuccess()) {
                        completer.ready(_dbClient);
                    } else if (cmdResult.getCommandPending()) {
                        completer.statusPending(_dbClient, cmdResult.getMessage());
                    } else {
                        _log.error("Snapmirror delete schedule failed.");
                        completer.error(_dbClient, cmdResult.getServiceCoded());
                    }
                } else if (cmdResult.getCommandPending()) {
                    completer.statusPending(_dbClient, cmdResult.getMessage());
                } else {
                    _log.error("Snapmirror break/failover failed.");
                    completer.error(_dbClient, cmdResult.getServiceCoded());
                }
            } else if (cmdResult.getCommandPending()) {
                completer.statusPending(_dbClient, cmdResult.getMessage());
            } else {
                _log.error("Snapmirror quiesce failed.");
                completer.error(_dbClient, cmdResult.getServiceCoded());
            }
        } else if (cmdResult.getCommandPending()) {
            completer.statusPending(_dbClient, cmdResult.getMessage());
        } else {
            _log.error("Snapmirror delete schedule failed.");
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }

    }

    @Override
    public void cancelMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void refreshMirrorFileShareLink(StorageSystem system, FileShare source, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    BiosCommandResult doCreateSnapMirror(StorageSystem storage, String portGroup, String sourcePath, String destPath) {
        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();
        // make api call
        nApi.createSnapMirror(sourcePath, sourcePath, portGroup);
        return BiosCommandResult.createSuccessfulResult();
    }

    /**
     * Initialize Snapmirror and state will be set to "SnapMirrored"
     * 
     * @param sourceStorage
     * @param targetStorage
     * @param sourceFs
     * @param targetFs
     * @param taskCompleter
     * @return
     */
    public BiosCommandResult doInitializeSnapMirror(StorageSystem sourceStorage, StorageSystem targetStorage, FileShare sourceFs,
            FileShare targetFs, TaskCompleter taskCompleter) {
        // get source system name
        String sourceLocation = getLocation(sourceStorage, sourceFs);

        // target netapp
        String portGroupTarget = findVfilerName(targetFs);
        NetAppApi nApiTarget = new NetAppApi.Builder(targetStorage.getIpAddress(),
                targetStorage.getPortNumber(), targetStorage.getUsername(),
                targetStorage.getPassword()).https(true).vFiler(portGroupTarget).build();

        String destLocation = getLocation(nApiTarget, targetFs);

        _log.info("Initializing snapmirror for destination: {}", destLocation);

        SnapMirrorStatusInfo mirrorStatusInfo = nApiTarget.getSnapMirrorStateInfo(destLocation);

        if (SnapMirrorState.UNKNOWN.equals(mirrorStatusInfo.getMirrorState()) ||
                SnapMirrorState.READY.equals(mirrorStatusInfo.getMirrorState())) {
            // make api call
            nApiTarget.initializeSnapMirror(sourceLocation, destLocation, portGroupTarget);
            NetAppSnapMirrorStartJob snapMirrorStatusJob = new NetAppSnapMirrorStartJob(destLocation, targetStorage.getId(), taskCompleter);
            try {
                ControllerServiceImpl.enqueueJob(new QueueJob(snapMirrorStatusJob));
                return BiosCommandResult.createPendingResult();
            } catch (Exception e) {
                _log.error("Snapmirror start failed", e);
                ServiceError error = DeviceControllerErrors.netapp.jobFailed("Snapmirror start failed:" + e.getMessage());
                return BiosCommandResult.createErrorResult(error);
            }
        } else if (SnapMirrorState.PAUSE.equals(mirrorStatusInfo.getMirrorState())) {
            nApiTarget.resumeSnapMirror(destLocation, portGroupTarget);
            return BiosCommandResult.createSuccessfulResult();
        } else {
            _log.error("Snapmirror start failed");
            ServiceError error = DeviceControllerErrors.netapp.jobFailed("Snapmirror start operation failed:");
            return BiosCommandResult.createErrorResult(error);
        }

    }

    /**
     * Removes Snapmiror relation ship between source and target
     * 
     * @param sourceStorage
     * @param targetStorage
     * @param sourceFs
     * @param targetFs
     * @param taskCompleter
     * @return
     */
    public BiosCommandResult doReleaseSnapMirror(StorageSystem sourceStorage, StorageSystem targetStorage, FileShare sourceFs,
            FileShare targetFs, TaskCompleter taskCompleter) {

        // netapp source client
        NetAppApi nApiSource = new NetAppApi.Builder(sourceStorage.getIpAddress(),
                sourceStorage.getPortNumber(), sourceStorage.getUsername(),
                sourceStorage.getPassword()).https(true).build();

        // get source system name
        String sourceLocation = getLocation(nApiSource, sourceFs);

        SnapMirrorStatusInfo statusInfo = nApiSource.getSnapMirrorStateInfo(sourceLocation);

        if (statusInfo == null) {
            _log.info("Snapmirror already released on source: {}", sourceLocation);
            return BiosCommandResult.createSuccessfulResult();
        }

        // target netapp
        NetAppApi nApiTarget = new NetAppApi.Builder(targetStorage.getIpAddress(),
                targetStorage.getPortNumber(), targetStorage.getUsername(),
                targetStorage.getPassword()).https(true).build();

        String destLocation = getLocation(nApiTarget, targetFs);

        /* The snapmirror-release API removes a SnapMirror relationship on the source endpoint */
        _log.info("Calling snapmirror release on source: {}, target: {}", sourceLocation, destLocation);
        nApiSource.releaseSnapMirror(sourceLocation, destLocation);
        return BiosCommandResult.createSuccessfulResult();
        /*
         * NetAppSnapMirrorReleaseJob snapMirrorReleaseJob = new NetAppSnapMirrorReleaseJob(sourceLocation, sourceStorage.getId(),
         * taskCompleter);
         * try {
         * ControllerServiceImpl.enqueueJob(new QueueJob(snapMirrorReleaseJob));
         * _log.info("Submitting job to check the snapmirror release status on target: {}", destLocation);
         * return BiosCommandResult.createPendingResult();
         * } catch (Exception e) {
         * _log.error("Release Snapmirror failed", e);
         * ServiceError error = DeviceControllerErrors.netapp.jobFailed("Release Snapmirror failed:" + e.getMessage());
         * if (taskCompleter != null) {
         * taskCompleter.error(_dbClient, error);
         * }
         * return BiosCommandResult.createErrorResult(error);
         * }
         */
    }

    public BiosCommandResult deleteSnapMirrorSchedule(StorageSystem sourceStorage, StorageSystem targetStorage, FileShare sourceFs,
            FileShare targetFs, TaskCompleter taskCompleter) {

        // target netapp
        NetAppApi nApiTarget = new NetAppApi.Builder(targetStorage.getIpAddress(),
                targetStorage.getPortNumber(), targetStorage.getUsername(),
                targetStorage.getPassword()).https(true).build();

        String destLocation = getLocation(nApiTarget, targetFs);

        /* The snapmirror-release API removes a SnapMirror relationship on the source endpoint */
        nApiTarget.deleteSnapMirrorSchedule(destLocation);
        return BiosCommandResult.createSuccessfulResult();
    }

    /**
     * resync the replication between source and target volumes
     * 
     * @param storage
     * @param portGroup
     * @param sourcePath
     * @param destPath
     * @param taskCompleter
     * @return
     */
    public BiosCommandResult doResyncSnapMirror(StorageSystem storage, String portGroup, String sourcePath, String destPath,
            TaskCompleter taskCompleter) {
        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();
        // make api call
        nApi.resyncSnapMirror(sourcePath, destPath, portGroup);
        return BiosCommandResult.createSuccessfulResult();
    }

    /**
     * breaks replication seesion make target write enabled and source read only
     * 
     * @param sourceStorage
     * @param targetStorage
     * @param sourceFs
     * @param targetFs
     * @param taskCompleter
     * @return
     */
    public BiosCommandResult
            doFailoverSnapMirror(StorageSystem storage, FileShare fileShare, TaskCompleter taskCompleter) {

        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).build();
        // make api call
        String location = getLocation(nApi, fileShare);

        SnapMirrorStatusInfo mirrorStatusInfo = nApi.getSnapMirrorStateInfo(location);

        if (mirrorStatusInfo != null) {
            if (SnapMirrorState.SYNCRONIZED.equals(mirrorStatusInfo.getMirrorState())) {
                _log.info("Calling snapmirror break on path: {}", location);
                nApi.breakSnapMirror(location);
                NetAppSnapMirrorFailover snapMirrorStatusJob = new NetAppSnapMirrorFailover(location, storage.getId(), taskCompleter,
                        location);
                try {
                    ControllerServiceImpl.enqueueJob(new QueueJob(snapMirrorStatusJob));
                    return BiosCommandResult.createPendingResult();
                } catch (Exception e) {
                    _log.error("Snapmirror start failed", e);
                    ServiceError error = DeviceControllerErrors.netapp.jobFailed("Snapmirror start failed:" + e.getMessage());
                    return BiosCommandResult.createErrorResult(error);
                }
            } else if (SnapMirrorState.FAILOVER.equals(mirrorStatusInfo.getMirrorState())) {
                _log.info("Snapmirror is already broken-off: {}", location);
                return BiosCommandResult.createSuccessfulResult();
            } else {
                ServiceError error = DeviceControllerErrors.netapp.jobFailed("Snapmirror break operation failed, because of mirror state: "
                        + mirrorStatusInfo.toString());
                return BiosCommandResult.createErrorResult(error);
            }
        }

        return BiosCommandResult.createSuccessfulResult();

        /*
         * NetAppSnapMirrorStatusJob snapMirrorStatusJob = new NetAppSnapMirrorStatusJob(location, storage.getId(), taskCompleter,
         * location);
         * 
         * try {
         * ControllerServiceImpl.enqueueJob(new QueueJob(snapMirrorStatusJob));
         * return BiosCommandResult.createPendingResult();
         * } catch (Exception e) {
         * _log.error("Snapmirror quiesce failed", e);
         * ServiceError error = DeviceControllerErrors.netapp.jobFailed("Snapmirror break failed:" + e.getMessage());
         * if (taskCompleter != null) {
         * taskCompleter.error(_dbClient, error);
         * }
         * return BiosCommandResult.createErrorResult(error);
         * }
         */
    }

    /**
     * Pause mirror relation
     * 
     * @param sourceStorage
     * @param targetStorage
     * @param sourceFs
     * @param targetFs
     * @param taskCompleter
     * @return
     */
    public BiosCommandResult doPauseSnapMirror(StorageSystem targetStorage,
            FileShare targetFs, TaskCompleter taskCompleter) {
        // get vfiler
        String portGroupTarget = findVfilerName(targetFs);
        NetAppApi nApi = new NetAppApi.Builder(targetStorage.getIpAddress(),
                targetStorage.getPortNumber(), targetStorage.getUsername(),
                targetStorage.getPassword()).https(true).vFiler(portGroupTarget).build();
        // make api call
        String destLocation = getLocation(targetStorage, targetFs);
        SnapMirrorStatusInfo mirrorStatusInfo = nApi.getSnapMirrorStateInfo(destLocation);
        _log.info("Calling snapmirror quiesce on destination: {}", destLocation);
        if (SnapMirrorState.SYNCRONIZED.equals(mirrorStatusInfo.getMirrorState())) {
            nApi.quiesceSnapMirror(destLocation);
            return BiosCommandResult.createSuccessfulResult();
        } else {
            ServiceError error = DeviceControllerErrors.netapp
                    .jobFailed("Snapmirror Pause operation failed, because of mirror state should be snapMirrored: "
                            + mirrorStatusInfo
                                    .getMirrorState().toString());
            return BiosCommandResult.createErrorResult(error);
        }
    }

    /**
     * Pause mirror relation synchronously
     * 
     * @param sourceStorage
     * @param targetStorage
     * @param sourceFs
     * @param targetFs
     * @param taskCompleter
     * @return
     */
    public BiosCommandResult doPauseSnapMirrorSync(StorageSystem targetStorage,
            FileShare targetFs, TaskCompleter taskCompleter) {
        // get vfiler
        String destLocation = getLocation(targetStorage, targetFs);
        BiosCommandResult result = this.doPauseSnapMirror(targetStorage, targetFs, taskCompleter);
        if (result.getCommandSuccess()) {
            NetAppSnapMirrorQuiesceJob snapMirrorQuiesceJob = new NetAppSnapMirrorQuiesceJob(destLocation, targetStorage.getId(),
                    taskCompleter, "quiesceSnapmirrorJob");
            try {
                ControllerServiceImpl.enqueueJob(new QueueJob(snapMirrorQuiesceJob));
            } catch (Exception e) {
                ServiceError error = DeviceControllerErrors.netapp
                        .jobFailed(e.getMessage());
                return BiosCommandResult.createErrorResult(error);
            }
            _log.info("Job submitted to check the status of snapmirror quiesce on {}", destLocation);
            return BiosCommandResult.createPendingResult();
        }
        return BiosCommandResult.createErrorResult(result.getServiceCoded());
    }

    /**
     * resume the mirror relation
     * 
     * @param sourceStorage
     * @param targetStorage
     * @param sourceFs
     * @param targetFs
     * @param taskCompleter
     * @return
     */
    public BiosCommandResult doResumeSnapMirror(StorageSystem targetStorage, FileShare targetFs, TaskCompleter taskCompleter) {
        // get vfiler
        String portGroupTarget = findVfilerName(targetFs);
        NetAppApi nApi = new NetAppApi.Builder(targetStorage.getIpAddress(),
                targetStorage.getPortNumber(), targetStorage.getUsername(),
                targetStorage.getPassword()).https(true).vFiler(portGroupTarget).build();
        // make api call destination system
        String destLocation = getLocation(targetStorage, targetFs);

        SnapMirrorStatusInfo mirrorStatusInfo = nApi.getSnapMirrorStateInfo(destLocation);
        _log.info("Calling snapmirror quiesce on destination: {}", destLocation);
        if (SnapMirrorState.PAUSE.equals(mirrorStatusInfo.getMirrorState())) {
            nApi.resumeSnapMirror(destLocation, null);
        } else {
            ServiceError error = DeviceControllerErrors.netapp
                    .jobFailed("Snapmirror Resume operation failed, because of mirror state should be Paused: "
                            + mirrorStatusInfo
                                    .getMirrorState().toString());
            return BiosCommandResult.createErrorResult(error);
        }

        nApi.resumeSnapMirror(destLocation, portGroupTarget);
        return BiosCommandResult.createSuccessfulResult();
    }

    public BiosCommandResult doDeleteSnapMirrorSchedule(StorageSystem targetStorage, FileShare targetFs) {
        NetAppApi nApi = new NetAppApi.Builder(targetStorage.getIpAddress(),
                targetStorage.getPortNumber(), targetStorage.getUsername(),
                targetStorage.getPassword()).https(true).build();
        // make api call destination system
        String destLocation = getLocation(targetStorage, targetFs);
        nApi.deleteSnapMirrorSchedule(destLocation);
        return BiosCommandResult.createSuccessfulResult();
    }

    /**
     * set the mirror schedule policy
     * 
     * @param completer
     * 
     * @param storage -target file system
     * @param portGroup - vfiler name
     * @param vPool - Virtual pool
     * @param sourcePath - source location
     * @param targetPath - destination location
     * @return
     */
    public BiosCommandResult setScheduleSnapMirror(StorageSystem sourceStorage, StorageSystem targetStorage, FileShare sourceFs,
            FileShare targetFs, TaskCompleter completer) {
        VirtualPool vPool = _dbClient.queryObject(VirtualPool.class, sourceFs.getVirtualPool());
        Long rpo = vPool.getFrRpoValue();
        String rpoType = "days-of-week";

        switch (vPool.getFrRpoType()) {
            case "MINUTES":
                rpoType = "minutes";
                break;
            case "HOURS":
                rpoType = "hours";
                break;
            case "DAYS":
                rpoType = "days-of-month";
                break;
        }

        String sourcePath = getLocation(sourceStorage, sourceFs);

        String portGroupTarget = findVfilerName(targetFs);
        NetAppApi nApiTarget = new NetAppApi.Builder(targetStorage.getIpAddress(),
                targetStorage.getPortNumber(), targetStorage.getUsername(),
                targetStorage.getPassword()).https(true).vFiler(portGroupTarget).build();

        String targetPath = getLocation(nApiTarget, targetFs);
        _log.info("Set snapmirror schedule: RPO every {} {} between source:{}  and target: {}", rpo, rpoType, sourcePath, targetPath);
        nApiTarget.setScheduleSnapMirror(rpoType, String.valueOf(rpo), sourcePath, targetPath);
        String destLocation = getLocation(nApiTarget, targetFs);
        NetAppSnapMirrorCreateJob mirrorCreateJob = new NetAppSnapMirrorCreateJob(destLocation, targetStorage.getId(),
                completer, "createSnapMirror");

        try {
            ControllerServiceImpl.enqueueJob(new QueueJob(mirrorCreateJob));
            _log.info("Job submitted to check the snapmirror status of at target: {}", destLocation);
            return BiosCommandResult.createPendingResult();
        } catch (Exception e) {
            _log.error("Snapmirror start failed", e);
            ServiceError error = DeviceControllerErrors.netapp.jobFailed("Snapmirror create failed:" + e.getMessage());
            return BiosCommandResult.createErrorResult(error);
        }
    }

    public String getLocation(NetAppApi nApi, FileShare share) {
        StringBuilder builderLoc = new StringBuilder();

        Map<String, String> systeminfo = nApi.systemInfo();
        String systemName = systeminfo.get("system-name");

        builderLoc.append(systemName);
        builderLoc.append(":");
        builderLoc.append(share.getName());
        return builderLoc.toString();
    }

    public String getLocation(StorageSystem storage, FileShare share) {
        String portGroup = findVfilerName(share);
        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();

        return getLocation(nApi, share);

    }

    /**
     * Return the vFiler name associated with the file system. If a vFiler is not associated with
     * this file system, then it will return null.
     */
    private String findVfilerName(FileShare fs) {
        String portGroup = null;

        URI port = fs.getStoragePort();
        if (port == null) {
            _log.info("No storage port URI to retrieve vFiler name");
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
                        _log.debug("using port {} and vFiler {}", stPort.getPortNetworkId(), portGroup);
                    }
                }
            }
        }
        return portGroup;
    }

    @Override
    public void doModifyReplicationRPO(StorageSystem system, Long rpoValue, String rpoType, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

}
