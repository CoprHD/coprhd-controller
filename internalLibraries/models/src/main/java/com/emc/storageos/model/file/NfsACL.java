/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.file.NfsACLUpdateParams.NfsACLOperationErrorType;

@XmlRootElement
public class NfsACL implements Serializable {

    private static final long serialVersionUID = 1780598964262028652L;
    /*
     * Payload attributes
     */
    private URI fileSystemId;
    private URI snapshotId;
    private String domain;
    private String user;
    private String group;
    private String fileSystemPath;
    private String permission;

    /*
     * Other attributes - not part of payload
     */
    private boolean proceedToNextStep;
    private NfsACLOperationErrorType errorType;

    public boolean canProceedToNextStep() {
        return proceedToNextStep;
    }

    public void proceedToNextStep() {
        this.proceedToNextStep = true;
    }

    public void cancelNextStep(NfsACLOperationErrorType aclNotFound) {
        this.proceedToNextStep = false;
        this.errorType = aclNotFound;
    }

    public NfsACLOperationErrorType getErrorType() {
        return errorType;
    }

    @XmlElement(name = "filesystem_id")
    public URI getFileSystemId() {
        return fileSystemId;
    }

    @XmlElement(name = "snapshot_id")
    public URI getSnapshotId() {
        return snapshotId;
    }

    @XmlElement(name = "user")
    public String getUser() {
        return user;
    }

    @XmlElement(name = "group")
    public String getGroup() {
        return group;
    }

    @XmlElement(name = "fileSystem_path")
    public String getfileSystemPath() {
        return fileSystemPath;
    }

    @XmlElement(name = "permission")
    public String getPermission() {
        return permission;
    }

    @XmlElement(name = "domain")
    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public void setFileSystemId(URI fileSystemId) {
        this.fileSystemId = fileSystemId;
    }

    public void setSnapshotId(URI snapshotId) {
        this.snapshotId = snapshotId;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void setFileSystemPath(String fileSystemPath) {
        this.fileSystemPath = fileSystemPath;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("NfsACL [");
        if (fileSystemId != null) {
            builder.append("fileSystemId=");
            builder.append(fileSystemId);
            builder.append(", ");
        }
        if (snapshotId != null) {
            builder.append("snapshotId=");
            builder.append(snapshotId);
            builder.append(", ");
        }
        if (domain != null) {
            builder.append("domain=");
            builder.append(domain);
            builder.append(", ");
        }
        if (user != null) {
            builder.append("user=");
            builder.append(user);
            builder.append(", ");
        }
        if (group != null) {
            builder.append("group=");
            builder.append(group);
            builder.append(", ");
        }
        if (fileSystemPath != null) {
            builder.append("fileSystemPath=");
            builder.append(fileSystemPath);
            builder.append(", ");
        }

        if (permission != null) {
            builder.append("permission=");
            builder.append(permission);
        }
        builder.append("]");
        return builder.toString();
    }

    public NfsACL() {

    }

}
