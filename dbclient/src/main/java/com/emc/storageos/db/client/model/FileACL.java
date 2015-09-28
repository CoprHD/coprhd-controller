/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

public abstract class FileACL extends DataObject {

    protected String user;
    protected String group;
    protected String domain;

    // Name of the cifs Export
    protected String fileSystemPath;

    // Permissions for user or group: read(r), change (rw) or full control
    protected String permission;

    // deviceExportId is the uid of the export on the array. Currently Isilon uses it
    // NetApp and VNXFile don't use this field.
    protected String deviceExportPath;

    protected String fileSystemExportACLIndex;
    protected String snapshotExportACLIndex;

    public static enum SupportedPermissions {
        read, change, fullcontrol
    }

    @Name("user")
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
        setChanged("user");
    }

    @Name("group")
    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
        setChanged("group");
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

    public void setgetFileSystemPath(String fileSystemPath) {
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

    @Name("deviceExportPath")
    public String getDeviceExportPath() {
        return deviceExportPath;
    }

    public void setDeviceExportPath(String deviceExportPath) {
        this.deviceExportPath = deviceExportPath;
        setChanged("deviceExportPath");
    }

    @Name("fileSystemExportACLIndex")
    @AlternateId("fileSystemExportACLIndexTable")
    public String getfileSystemExportACLIndex() {
        return fileSystemExportACLIndex;
    }

    public void setfileSystemExportACLIndex(String fileSystemExportACLIndex) {
        this.fileSystemExportACLIndex = fileSystemExportACLIndex;
        setChanged("fileSystemExportACLIndex");
    }

    @Name("snapshotExportACLIndex")
    @AlternateId("snapshotExportACLIndexTable")
    public String getsnapshotExportACLIndex() {
        return snapshotExportACLIndex;
    }

    public void setsnapshotExportACLIndex(String snapshotExportACLIndex) {
        this.snapshotExportACLIndex = snapshotExportACLIndex;
        setChanged("snapshotExportACLIndex");
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ExportACL [user=");
        builder.append(user);
        builder.append(", group=");
        builder.append(group);
        builder.append(", fileSystemPath=");
        builder.append(fileSystemPath);
        builder.append(", permission=");
        builder.append(permission);
        builder.append(", deviceExportPath=");
        builder.append(deviceExportPath);
        builder.append("]");
        return builder.toString();
    }

    public abstract void calculateACLIndex();
}
