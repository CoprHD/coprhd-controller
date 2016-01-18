/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileDeviceInputOutput;
import com.emc.storageos.volumecontroller.FileStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/*
 * Default implementation of FileStorageDevice, so that subclass can just overwrite necessary methods.
 */
public abstract class AbstractFileStorageDevice implements FileStorageDevice,
        RemoteFileMirrorOperation {

    @Override
    public void doCreateMirrorLink(StorageSystem system, URI source, URI target, TaskCompleter completer) {
        // TODO Auto-generated method stub
        throw DeviceControllerException.exceptions.operationNotSupported();

    }
    
    @Override
    public void doRollbackMirrorLink(StorageSystem system, List<URI> sources,
                                     List<URI> targets, TaskCompleter completer) {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public void doDetachMirrorLink(StorageSystem system, URI source, URI target,
            TaskCompleter completer) {
        // TODO Auto-generated method stub
        throw DeviceControllerException.exceptions.operationNotSupported();
    }
    

    @Override
    public void doStartMirrorLink(StorageSystem system, FileShare target,
            TaskCompleter completer) {
        // TODO Auto-generated method stub
        throw DeviceControllerException.exceptions.operationNotSupported();
    }
    
    /**
     * Cancel a replication link.
     *
     * @param system
     * @param target
     * @param completer
     */
    @Override
    public void doCancelMirrorLink(StorageSystem system, FileShare target, TaskCompleter completer){
        throw DeviceControllerException.exceptions.operationNotSupported();
    }
    

    @Override
    public BiosCommandResult doCreateFS(StorageSystem storage,
            FileDeviceInputOutput fd) throws ControllerException {
        // TODO Auto-generated method stub
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public boolean doCheckFSExists(StorageSystem storage,
            FileDeviceInputOutput fd) throws ControllerException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public BiosCommandResult doDeleteFS(StorageSystem storage,
            FileDeviceInputOutput fd) throws ControllerException {
        // TODO Auto-generated method stub
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult doExport(StorageSystem storage,
            FileDeviceInputOutput fd, List<FileExport> exportList)
            throws ControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult doShare(StorageSystem storage,
            FileDeviceInputOutput args, SMBFileShare smbFileShare)
            throws ControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult doDeleteShare(StorageSystem storage,
            FileDeviceInputOutput args, SMBFileShare smbFileShare)
            throws ControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult doDeleteShares(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult doUnexport(StorageSystem storage,
            FileDeviceInputOutput fd, List<FileExport> exportList)
            throws ControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult doModifyFS(StorageSystem storage,
            FileDeviceInputOutput fd) throws ControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult doExpandFS(StorageSystem storage,
            FileDeviceInputOutput fd) throws ControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult doSnapshotFS(StorageSystem storage,
            FileDeviceInputOutput fd) throws ControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult doRestoreFS(StorageSystem storage,
            FileDeviceInputOutput fd) throws ControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult getFSSnapshotList(StorageSystem storage,
            FileDeviceInputOutput fd, List<String> snapshots)
            throws ControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult doDeleteSnapshot(StorageSystem storage,
            FileDeviceInputOutput fd) throws ControllerException {
        // TODO Auto-generated method stub
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public void doConnect(StorageSystem storage) throws ControllerException {
        // TODO Auto-generated method stub
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public void doDisconnect(StorageSystem storage) throws ControllerException {
        // TODO Auto-generated method stub
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult getPhysicalInventory(StorageSystem storage) {
        // TODO Auto-generated method stub
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult doCreateQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput fd, QuotaDirectory qt)
            throws ControllerException {
        // TODO Auto-generated method stub
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult doDeleteQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput fd) throws ControllerException {
        // TODO Auto-generated method stub
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult doUpdateQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput fd, QuotaDirectory qt)
            throws ControllerException {
        // TODO Auto-generated method stub
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult updateExportRules(StorageSystem storage,
            FileDeviceInputOutput args) {
        // TODO Auto-generated method stub
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult deleteExportRules(StorageSystem storage,
            FileDeviceInputOutput args) {
        // TODO Auto-generated method stub
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult updateShareACLs(StorageSystem storage,
            FileDeviceInputOutput args) {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult deleteShareACLs(StorageSystem storageObj,
            FileDeviceInputOutput args) {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult updateNfsACLs(StorageSystem storage,
            FileDeviceInputOutput args) {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public BiosCommandResult deleteNfsACLs(StorageSystem storageObj,
            FileDeviceInputOutput args) {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public void doCreateMirror(StorageSystem storage, URI mirror,
            Boolean createInactive, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        // TODO Auto-generated method stub
        throw DeviceControllerException.exceptions.operationNotSupported();

    }

    @Override
    public void doDeleteMirror(StorageSystem storage, URI mirror,
            Boolean createInactive, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        // TODO Auto-generated method stub
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

}
