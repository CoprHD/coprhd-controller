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

        // send netapp api call

        String portGroup = findVfilerName(sourceFileShare);
        BiosCommandResult cmdResult = doCreateSnapMirror(sourceStorageSystem, sourceFileShare.getPath(), targetFileShare.getPath(),
                portGroup);
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
        BiosCommandResult cmdResult = doStopReplicationPolicy(system, portGroup, sourceFileShare.getMountPath(), target.getMountPath(),
                completer);
        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }

    }

    @Override
    public void startMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException {
        FileShare sourceFileShare = _dbClient.queryObject(FileShare.class, target.getParentFileShare().getURI());
        VirtualPool vPool = _dbClient.queryObject(VirtualPool.class, sourceFileShare.getVirtualPool());
        String portGroup = findVfilerName(sourceFileShare);
        BiosCommandResult cmdResult = setScheduleSnapMirror(system, portGroup, vPool, sourceFileShare.getMountPath(), target.getMountPath());

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
    public void failoverMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void resyncMirrorFileShareLink(StorageSystem primarySystem, StorageSystem secondarySystem, FileShare target,
            TaskCompleter completer, String policyName) {
        // TODO Auto-generated method stub

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

    public BiosCommandResult doStartSnapMirror(StorageSystem storage, String portGroup, String sourcePath, String targetPath,
            TaskCompleter taskCompleter) {

        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();
        // make api call
        nApi.initializeSnapMirror(sourcePath, sourcePath, portGroup);
        return BiosCommandResult.createSuccessfulResult();
    }

    public BiosCommandResult setScheduleSnapMirror(StorageSystem storage, String portGroup, VirtualPool vPool, String sourcePath,
            String targetPath) {
        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();
        // make api call
        String type = "";
        String scheduleTime = "";

        nApi.setScheduleSnapMirror(type, scheduleTime, sourcePath, targetPath, portGroup);
        return BiosCommandResult.createSuccessfulResult();
    }

    public BiosCommandResult doStopReplicationPolicy(StorageSystem storage, String portGroup, String sourcePath, String destPath,
            TaskCompleter taskCompleter) {
        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();
        // make api call
        nApi.destorySnapMirror(sourcePath, destPath, portGroup);
        return BiosCommandResult.createSuccessfulResult();
    }

}
