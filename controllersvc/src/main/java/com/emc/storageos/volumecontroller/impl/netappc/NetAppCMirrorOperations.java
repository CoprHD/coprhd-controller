package com.emc.storageos.volumecontroller.impl.netappc;

import java.net.URI;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.file.FileMirrorOperations;

public class NetAppCMirrorOperations implements FileMirrorOperations {

    private DbClient _dbClient;

    public DbClient get_dbClient() {
        return _dbClient;
    }

    public void set_dbClient(DbClient _dbClient) {
        this._dbClient = _dbClient;
    }

    public NetAppCMirrorOperations() {
        // TODO Auto-generated constructor stub
    }

    @Override
    public void createMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void stopMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void startMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException {
        // TODO Auto-generated method stub

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

    @Override
    public void doModifyReplicationRPO(StorageSystem system, Long rpoValue, String rpoType, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

}
