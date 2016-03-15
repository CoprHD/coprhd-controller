package com.emc.storageos.volumecontroller.impl.netapp;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.FileReplicationType;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.netapp.NetAppApi;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.file.FileMirrorOperations;

public class NetappMirrorFileOperations implements FileMirrorOperations {
    private static final Logger _log = LoggerFactory
            .getLogger(NetappMirrorFileOperations.class);

    private DbClient _dbClient;

    public void setDbClient(DbClient dbClient) {
        this._dbClient = dbClient;
    }

    public NetappMirrorFileOperations() {
        // TODO Auto-generated constructor stub
    }

    @Override
    public void createMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {
        // TODO Auto-generated method stub
        FileShare sourceFileShare = _dbClient.queryObject(FileShare.class, source);
        FileShare targetFileShare = _dbClient.queryObject(FileShare.class, target);

        StorageSystem sourceStorageSystem = _dbClient.queryObject(StorageSystem.class, sourceFileShare.getStorageDevice());
        StorageSystem targetStorageSystem = _dbClient.queryObject(StorageSystem.class, targetFileShare.getStorageDevice());

        BiosCommandResult cmdResult = null;
        // send netapp api call
        String portGroup = findVfilerName(sourceFileShare);
        VirtualPool virtualPool = _dbClient.queryObject(VirtualPool.class, sourceFileShare.getVirtualPool());

        if (virtualPool.getFileReplicationType().equals(FileReplicationType.LOCAL.toString())) {
            cmdResult = doCreateSnapMirror(targetStorageSystem, portGroup,
                    sourceFileShare.getMountPath(), targetFileShare.getPath());
        } else {
            cmdResult = doCreateSnapMirror(targetStorageSystem, portGroup,
                    sourceFileShare.getMountPath(), targetFileShare.getMountPath());
        }

        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }
    }

    @Override
    public void stopMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {
        // TODO Auto-generated method stub
        FileShare sourceFileShare = _dbClient.queryObject(FileShare.class, target.getParentFileShare().getURI());
        String portGroup = findVfilerName(sourceFileShare);
        BiosCommandResult cmdResult = doStopSnapMirror(system, portGroup, sourceFileShare.getMountPath(), target.getMountPath(),
                completer);
        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }

    }

    @Override
    public void startMirrorFileShareLink(StorageSystem system, FileShare targetFileShare, TaskCompleter completer, String policyName)
            throws DeviceControllerException {

        FileShare sourceFileShare = _dbClient.queryObject(FileShare.class, targetFileShare.getParentFileShare().getURI());
        StorageSystem targetStorageSystem = _dbClient.queryObject(StorageSystem.class, targetFileShare.getStorageDevice());
        VirtualPool vPool = _dbClient.queryObject(VirtualPool.class, sourceFileShare.getVirtualPool());

        BiosCommandResult cmdResult = null;
        // send netapp api call
        String portGroup = findVfilerName(sourceFileShare);
        VirtualPool virtualPool = _dbClient.queryObject(VirtualPool.class, sourceFileShare.getVirtualPool());

        // initialize the snap mirror
        if (virtualPool.getFileReplicationType().equals(FileReplicationType.LOCAL.toString())) {
            cmdResult = doInitializeSnapMirror(targetStorageSystem, portGroup,
                    sourceFileShare.getMountPath(), targetFileShare.getPath(), completer);
        } else {
            cmdResult = doInitializeSnapMirror(targetStorageSystem, portGroup,
                    sourceFileShare.getMountPath(), targetFileShare.getMountPath(), completer);
        }
        // set the schedule time
        if (cmdResult.getCommandSuccess()) {
            cmdResult = setScheduleSnapMirror(targetStorageSystem, portGroup, vPool, sourceFileShare.getMountPath(),
                    targetFileShare.getMountPath());
            if (cmdResult.getCommandSuccess()) {
                completer.ready(_dbClient);
            } else {
                completer.error(_dbClient, cmdResult.getServiceCoded());
            }

        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }
    }

    @Override
    public void pauseMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void resumeMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void failoverMirrorFileShareLink(StorageSystem system, FileShare targetFileShare, TaskCompleter completer, String policyName)
            throws DeviceControllerException {

        StorageSystem targetStorageSystem = _dbClient.queryObject(StorageSystem.class, targetFileShare.getStorageDevice());
        String portGroup = findVfilerName(targetFileShare);
        BiosCommandResult cmdResult = doFailoverSnapMirror(system, portGroup, targetFileShare.getMountPath(), completer);

        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }

    }

    @Override
    public void resyncMirrorFileShareLink(StorageSystem primarySystem, StorageSystem secondarySystem, FileShare fileshare,
            TaskCompleter completer, String policyName) {
        // TODO Auto-generated method stub
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
    public void deleteMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {
        // TODO Auto-generated method stub

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

    BiosCommandResult doCreateSnapMirror(StorageSystem storage, String portGroup, String sourcePath, String destPath) {
        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();
        // make api call
        nApi.createSnapMirror(sourcePath, sourcePath, portGroup);
        return BiosCommandResult.createSuccessfulResult();
    }

    public BiosCommandResult doInitializeSnapMirror(StorageSystem storage, String portGroup, String sourcePath, String targetPath,
            TaskCompleter taskCompleter) {

        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();
        // make api call
        nApi.initializeSnapMirror(sourcePath, sourcePath, portGroup);
        return BiosCommandResult.createSuccessfulResult();
    }

    public BiosCommandResult doStopSnapMirror(StorageSystem storage, String portGroup, String sourcePath, String targetPath,
            TaskCompleter taskCompleter) {

        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();
        // make api call
        nApi.destorySnapMirror(sourcePath, sourcePath, portGroup);
        return BiosCommandResult.createSuccessfulResult();
    }

    public BiosCommandResult doResyncSnapMirror(StorageSystem storage, String portGroup, String sourcePath, String destPath,
            TaskCompleter taskCompleter) {
        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();
        // make api call
        nApi.resyncSnapMirror(sourcePath, destPath, portGroup);
        return BiosCommandResult.createSuccessfulResult();
    }

    public BiosCommandResult
            doFailoverSnapMirror(StorageSystem storage, String portGroup, String pathLocation, TaskCompleter taskCompleter) {
        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();
        // make api call
        nApi.breakSnapMirror(pathLocation, portGroup);
        return BiosCommandResult.createSuccessfulResult();
    }

    /**
     * 
     * @param storage -target file system
     * @param portGroup - vfiler name
     * @param vPool - Virtual pool
     * @param sourcePath - source location
     * @param targetPath - destination location
     * @return
     */
    public BiosCommandResult setScheduleSnapMirror(StorageSystem storage, String portGroup, VirtualPool vPool, String sourcePath,
            String targetPath) {
        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();

        Long rpo = vPool.getFrRpoValue();
        String rpoType = "days-of-week";
        String rpoValue = "-";

        switch (vPool.getFrRpoType()) {
            case "MINUTES":
                rpoType = "minutes";
                rpoValue = rpoType;
                break;
            case "HOURS":
                rpoType = "hours";
                rpoValue = rpoType;
                break;
            case "DAYS":
                rpoType = "days-of-month";
                rpoValue = rpoType;
                break;
        }

        // make api call

        nApi.setScheduleSnapMirror(rpoType, rpoValue, sourcePath, targetPath, portGroup);
        return BiosCommandResult.createSuccessfulResult();
    }

}
