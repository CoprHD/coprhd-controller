/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;


/**
 * Attributes associated with file share, specified during the
 * file share creation.
 *
 */
public class FileSystemShareBase {
    
    private String shareName;
    private String description;
    private String maxUsers = "unlimited";  // default --- unlimited
    private String permissionType = "allow"; //FileSMBShare.PermissionType.allow.name();
    private String permission; //FileSMBShare.Permission.change.name();
    private String subDirectory;

    public FileSystemShareBase() {}
    
    public FileSystemShareBase(String shareName, String description,
            String maxUsers, String permissionType, String permission, String subDirectory) {
        this.shareName = shareName;
        this.description = description;
        this.maxUsers = maxUsers;
        this.permissionType = permissionType;
        this.permission = permission;
        this.subDirectory = subDirectory;
    }

    /**
     * User provided name of the file share.
     * @valid none
     */
    @XmlElement(name = "name", required = true)
    public String getShareName() {
        return shareName;
    }

    public void setShareName(String shareName) {
        this.shareName = shareName;
    }

    /**
     * User provided description of the file share.
     * @valid none
     */
    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Maximum number of users of the file share.  Default
     * is unlimited.
     * @valid none
     */
    @XmlElement(name = "max_users")
    public String getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(String maxUsers) {
        this.maxUsers = maxUsers;
    }

    /**
     * Permission type for the file share.  Default is
     * "allow".
     * @valid "allow" = permission type by default
     * @valid "deny"
     */
    @XmlElement(name = "permission_type")
    public String getPermissionType() {
        return permissionType;
    }

    public void setPermissionType(String permissionType) {
        this.permissionType = permissionType;
    }

    /**
     * Permission type for the file share.  Default is
     * "change".
     * @valid "read" = read permission only
     * @valid "change" = read and write permissions
     * @valid "full" = full control of the file share
     */
    @XmlElement(name = "permission")
    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    @XmlElement(name = "subDirectory")
    public String getSubDirectory() {
        return subDirectory;
    }

    public void setSubDirectory(String subDirectory) {
        this.subDirectory = subDirectory;
    }
    
}
