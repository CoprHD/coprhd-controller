/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.io.Serializable;

import com.emc.storageos.db.client.model.SMBFileShare;

/**
 * FileSMBShare class keeps SMB share data. Transient class.
 */
public class FileSMBShare implements Serializable {

    // enumeration of SMB share permission types
    public enum PermissionType {
        allow,
        deny
    }

    // enumeration of SMB share permissions
    public enum Permission {
        read,
        change,
        full
    }

    // Apply Windows Default ACLs | false: Do not change existing permissions
    public enum SMBDirectoryPermissionOption {
        DoNotChangeExistingPermissions,
        ApplyWindowsDefaultACLs
    }

    private String _name;
    private String _description;
    private PermissionType _permissionType;
    private Permission _permission;
    private int _maxUsers;
    private String _storagePortName;
    private String _storagePortNetworkId;
    private String _storagePortGroup;
    private String _nativeId;
    private String _mountPoint;
    private String _path;
    private boolean _isSubDirPath;
    private String _NetBIOSName;
    private String _directoryAclsOptions;

    /**
     * Construction of SMB share
     * 
     * @param name
     * @param description
     * @param permissionType
     * @param permission
     * @param maxUsers
     */
    public FileSMBShare(String name, String description, String permissionType, String permission,
            String maxUsers, String nativeId, String path) {
        this._name = name;
        this._description = description;
        this._path = path;
        // convert permission and permissionType to lower case to avoid unnecessary exception
        try {
            this._permissionType = Enum.valueOf(PermissionType.class, permissionType.toLowerCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Permission Type:  Must be one of \"allow\" or \"deny\"");
        }

        try {
            this._permission = Enum.valueOf(Permission.class, permission.toLowerCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Permission:  Must be one of \"read\" or \"change\" or \"full\"");
        }

        this._maxUsers = Integer.parseInt(maxUsers);
        this._nativeId = nativeId;
    }

    public FileSMBShare(String name, String description, String maxUsers, String nativeId) {
        super();
        this._name = name;
        this._description = description;
        this._maxUsers = Integer.parseInt(maxUsers);
        this._nativeId = nativeId;

    }

    public FileSMBShare(String name, String description, String maxUsers) {
        this(name, description, maxUsers, null);
    }

    /**
     * Construction of FileSMBShare
     * 
     * @param smb SMBFileShare
     */
    public FileSMBShare(SMBFileShare smb) {
        this._name = smb.getName();
        this._description = smb.getDescription();
        this._permissionType = Enum.valueOf(PermissionType.class, smb.getPermissionType());
        this._permission = Enum.valueOf(Permission.class, smb.getPermission());
        this._maxUsers = smb.getMaxUsers();
        this._nativeId = smb.getNativeId();
        this._mountPoint = smb.getMountPoint();
        this._path = smb.getPath();
        this._isSubDirPath = Boolean.valueOf(smb.isSubdir());
        this._directoryAclsOptions = smb.getDirectoryAclsOptions();
    }

    public String getName() {
        return _name;
    }

    public String getDescription() {
        return _description;
    }

    public PermissionType getPermissionType() {
        return _permissionType;
    }

    public Permission getPermission() {
        return _permission;
    }

    public int getMaxUsers() {
        return _maxUsers;
    }

    public String getStoragePortName() {
        return _storagePortName;
    }

    public void setStoragePortName(String storagePortName) {
        this._storagePortName = storagePortName;
    }

    public String getStoragePortNetworkId() {
        return _storagePortNetworkId;
    }

    public void setStoragePortNetworkId(String storagePortNetworkId) {
        this._storagePortNetworkId = storagePortNetworkId;
    }

    public String getNetBIOSName() {
        return _NetBIOSName;
    }

    public void setNetBIOSName(String NetBIOSName) {
        this._NetBIOSName = NetBIOSName;
    }

    public String getStoragePortGroup() {
        return _storagePortGroup;
    }

    public void setStoragePortGroup(String storagePortGroup) {
        this._storagePortGroup = storagePortGroup;
    }

    public String getNativeId() {
        return _nativeId;
    }

    public void setNativeId(String nativeId) {
        this._nativeId = nativeId;
    }

    public void setPath(String path) {
        this._path = path;
    }

    public String getPath() {
        return _path;
    }

    public boolean isSubDirPath() {
        return _isSubDirPath;
    }

    public void setSubDirPath(boolean isSubDirPath) {
        this._isSubDirPath = isSubDirPath;
    }

    public String getDirectoryAclsOptions() {
        return _directoryAclsOptions;
    }

    public void setDirectoryAclsOptions(String directoryAclsOptions) {
        this._directoryAclsOptions = directoryAclsOptions;
    }

    public SMBFileShare getSMBFileShare() {

        SMBFileShare smbShare = new SMBFileShare(_name, _description, _permissionType.toString(),
                _permission.toString(), _maxUsers, _mountPoint);
        smbShare.setNativeId(_nativeId);
        smbShare.setPortGroup(_storagePortGroup);
        smbShare.setPath(_path);
        smbShare.setSubDir(_isSubDirPath);
        smbShare.setStoragePortName(_storagePortName);
        smbShare.setStoragePortNetworkId(_storagePortNetworkId);
        smbShare.setNetBIOSName(_NetBIOSName);
        smbShare.setDirectoryAclsOptions(_directoryAclsOptions);
        return smbShare;

    }
}
