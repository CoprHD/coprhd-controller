/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

public abstract class FileACL extends DataObject {

    protected String user;
    protected String type;
    protected String domain;

    protected String fileSystemPath;

    // Permissions for user or group: read(r), change (rw) or full control
    protected String permission;

    // permissionType can be allow or deny
    protected String permissionType;

    protected String fileSystemACLIndex;

    protected String snapshotACLIndex;

    @Name("user")
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
        setChanged("user");
    }

    @Name("type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
        setChanged("type");
    }

    @Name("domain")
    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
        setChanged("domain");
    }

    @Name("fileSystemPath")
    public String getFileSystemPath() {
        return fileSystemPath;
    }

    public void setFileSystemPath(String fileSystemPath) {
        this.fileSystemPath = fileSystemPath;
        setChanged("fileSystemPath");
    }

    @Name("permission")
    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
        setChanged("permission");
    }

    @Name("fileSystemACLIndex")
    @AlternateId("fileSystemACLIndexTable")
    public String getFileSystemACLIndex() {
        return fileSystemACLIndex;
    }

    public void setFileSystemACLIndex(String fileSystemACLIndex) {
        this.fileSystemACLIndex = fileSystemACLIndex;
        setChanged("fileSystemExportACLIndex");
    }

    @Name("snapshotACLIndex")
    @AlternateId("snapshotACLIndexTable")
    public String getSnapshotACLIndex() {
        return snapshotACLIndex;
    }

    public void setSnapshotACLIndex(String snapshotACLIndex) {
        this.snapshotACLIndex = snapshotACLIndex;
        setChanged("snapshotACLIndex");
    }

    @Name("permission_type")
    public String getPermissionType() {
        return permissionType;
    }

    public void setPermissionType(String permissionType) {
        this.permissionType = permissionType;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ACL [user=");
        builder.append(user);
        builder.append(", type=");
        builder.append(type);
        builder.append(", fileSystemPath=");
        builder.append(fileSystemPath);
        builder.append(", permission=");
        builder.append(permission);
        builder.append(", permission type=");
        builder.append(permissionType);
        builder.append("]");
        return builder.toString();
    }

    public abstract void calculateACLIndex();
}
