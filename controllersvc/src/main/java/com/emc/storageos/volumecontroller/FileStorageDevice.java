/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import java.util.List;

import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;

/**
 * Main interface for block storage device specific implementations.
 * 
 */
public interface FileStorageDevice {

    /**
     * 
     * @param storage storage device operation is to be performed on
     * @param fd FileDeviceInputOutput object holding the data objects
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult doCreateFS(StorageSystem storage, FileDeviceInputOutput fd)
            throws ControllerException;

    /**
     * 
     * @param storage storage device operation is to be performed on
     * @param fd FileDeviceInputOutput object holding the data objects
     * @return true if FS exists on Array else false
     * @throws ControllerException
     */
    public boolean doCheckFSExists(StorageSystem storage, FileDeviceInputOutput fd)
            throws ControllerException;

    /**
     * 
     * @param storage storage device operation is to be performed on
     * @param fd FileDeviceInputOutput object holding the data objects
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult doDeleteFS(StorageSystem storage, FileDeviceInputOutput fd)
            throws ControllerException;

    /**
     * 
     * @param storage storage device operation is to be performed on
     * @param fd FileDeviceInputOutput object holding the data objects
     * @param exportList export list
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult doExport(StorageSystem storage, FileDeviceInputOutput fd,
            List<FileExport> exportList) throws ControllerException;

    /**
     * Create/modify SMB share
     * 
     * @param storage storage device operation is to be performed on
     * @param args FileDeviceInputOutput object with data about file system to be shared
     * @param smbFileShare smb share properties
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult doShare(StorageSystem storage, FileDeviceInputOutput args,
            SMBFileShare smbFileShare) throws ControllerException;

    /**
     * Delete SMB share of file system
     * 
     * @param storage storage device operation is to be performed on
     * @param args FileDeviceInputOutput object with data about file system on which operation is performed
     * @param smbFileShare smb share properties
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult doDeleteShare(StorageSystem storage, FileDeviceInputOutput args,
            SMBFileShare smbFileShare) throws ControllerException;

    /**
     * Delete all SMB shares of file system
     * 
     * @param storage storage device operation is to be performed on
     * @param args FileDeviceInputOutput object with data about file system on which operation is performed
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult doDeleteShares(StorageSystem storage, FileDeviceInputOutput args) throws ControllerException;

    /**
     * 
     * @param storage storage device operation is to be performed on
     * @param fd FileDeviceInputOutput object holding the data objects
     * @param exportList unexport list
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult doUnexport(StorageSystem storage, FileDeviceInputOutput fd,
            List<FileExport> exportList) throws ControllerException;

    /**
     * 
     * @param storage storage device operation is to be performed on
     * @param fd FileDeviceInputOutput object holding the data objects
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult doModifyFS(StorageSystem storage, FileDeviceInputOutput fd)
            throws ControllerException;

    /**
     * 
     * @param storage storage device operation is to be performed on
     * @param fd FileDeviceInputOutput object holding the data objects
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult doExpandFS(StorageSystem storage, FileDeviceInputOutput fd)
            throws ControllerException;

    /**
     * 
     * @param storage
     * @param fd FileDeviceInputOutput object holding the data objects
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult doSnapshotFS(StorageSystem storage, FileDeviceInputOutput fd)
            throws ControllerException;

    /**
     * 
     * @param storage
     * @param fd FileDeviceInputOutput object holding the data objects
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult doRestoreFS(StorageSystem storage, FileDeviceInputOutput fd)
            throws ControllerException;

    /**
     * 
     * @param storage
     * @param fd FileDeviceInputOutput object holding the data objects
     * @param snapshots List of snapshot names found on the device
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult getFSSnapshotList(StorageSystem storage, FileDeviceInputOutput fd,
            List<String> snapshots) throws ControllerException;

    /**
     * 
     * @param storage storage device operation is to be performed on
     * @param fd FileDeviceInputOutput object holding the data objects
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult doDeleteSnapshot(StorageSystem storage, FileDeviceInputOutput fd) throws ControllerException;

    /**
     * Connect the device - called when a new device is added
     * 
     * @param storage storage device object
     * @return command result object
     * @throws ControllerException
     */
    public void doConnect(StorageSystem storage) throws ControllerException;

    /**
     * Disconnect the device - called when a device is being removed
     * 
     * @param storage storage device object
     * @return command result object
     * @throws ControllerException
     */
    public void doDisconnect(StorageSystem storage) throws ControllerException;

    /**
     * Obtain the physical pools and ports
     * 
     * @param storage storage device object
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult getPhysicalInventory(StorageSystem storage);

    /**
     * 
     * @param storage
     * @param fd FileDeviceInputOutput object holding the data objects
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult doCreateQuotaDirectory(StorageSystem storage, FileDeviceInputOutput fd, QuotaDirectory qt)
            throws ControllerException;

    /**
     * 
     * @param storage
     * @param fd FileDeviceInputOutput object holding the data objects
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult doDeleteQuotaDirectory(StorageSystem storage, FileDeviceInputOutput fd)
            throws ControllerException;

    public BiosCommandResult doUpdateQuotaDirectory(StorageSystem storage, FileDeviceInputOutput fd, QuotaDirectory qt)
            throws ControllerException;

    public BiosCommandResult updateExportRules(StorageSystem storage,
            FileDeviceInputOutput args);

    public BiosCommandResult deleteExportRules(StorageSystem storage,
            FileDeviceInputOutput args);

    public BiosCommandResult updateShareACLs(StorageSystem storage,
            FileDeviceInputOutput args);

    public BiosCommandResult deleteShareACLs(StorageSystem storageObj,
            FileDeviceInputOutput args);

    public BiosCommandResult updateNfsACLs(StorageSystem storage,
            FileDeviceInputOutput args);

    public BiosCommandResult deleteNfsACLs(StorageSystem storageObj,
            FileDeviceInputOutput args);
}