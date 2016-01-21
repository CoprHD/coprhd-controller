/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

/**
 * FileACL will contain the details of ACL on File.
 * It will hold information about the user, type, domain, fileSystemPath, permissionType etc. mapped to File ACL
 * 
 * @author sauraa
 * 
 */
public abstract class FileACL extends DataObject {

    protected String user;
    protected String type;
    protected String domain;
    protected String fileSystemPath;

    // Permissions for user or group: read(r), write (w) or execute(x) comma separated.
    protected String permissions;

    // permissionType can be allow or deny
    protected String permissionType;

    protected String fileSystemNfsACLIndex;

    protected String snapshotNfsACLIndex;

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

    @Name("permissions")
    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
        setChanged("permissions");
    }

    @Name("fileSystemNfsACLIndex")
    @AlternateId("fileSystemNfsACLIndexTable")
    public String getFileSystemNfsACLIndex() {
        return fileSystemNfsACLIndex;
    }

    public void setFileSystemNfsACLIndex(String fileSystemNfsACLIndex) {
        this.fileSystemNfsACLIndex = fileSystemNfsACLIndex;
        setChanged("fileSystemNfsACLIndex");
    }

    @Name("snapshotNfsACLIndex")
    @AlternateId("snapshotNfsACLIndexTable")
    public String getSnapshotNfsACLIndex() {
        return snapshotNfsACLIndex;
    }

    public void setSnapshotNfsACLIndex(String snapshotNfsACLIndex) {
        this.snapshotNfsACLIndex = snapshotNfsACLIndex;
        setChanged("snapshotNfsACLIndex");
    }

    @Name("permission_type")
    public String getPermissionType() {
        return permissionType;
    }

    public void setPermissionType(String permissionType) {
        this.permissionType = permissionType;
        setChanged("permission_type");
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
        builder.append(", permissions=");
        builder.append(permissions);
        builder.append(", permission type=");
        builder.append(permissionType);
        builder.append("]");
        return builder.toString();
    }

    public abstract void calculateNfsACLIndex();
}
