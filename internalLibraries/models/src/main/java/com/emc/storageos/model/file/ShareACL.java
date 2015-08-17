/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.file.CifsShareACLUpdateParams.ShareACLOperationErrorType;

@XmlRootElement
public class ShareACL implements Serializable {

    private static final long serialVersionUID = 1780598964262028652L;
    /*
     * Payload attributes
     */
    private URI fileSystemId;
    private URI snapshotId;
    private String domain;
    private String user;
    private String group;
    private String shareName;
    private String permission;

    /*
     * Other attributes - not part of payload
     */
    private boolean proceedToNextStep;
    private ShareACLOperationErrorType errorType;

    public boolean canProceedToNextStep() {
        return proceedToNextStep;
    }

    public void proceedToNextStep() {
        this.proceedToNextStep = true;
    }

    public void cancelNextStep(ShareACLOperationErrorType errorType) {
        this.proceedToNextStep = false;
        this.errorType = errorType;
    }

    public ShareACLOperationErrorType getErrorType() {
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

    @XmlElement(name = "share_name")
    public String getShareName() {
        return shareName;
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

    public void setShareName(String shareName) {
        this.shareName = shareName;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ShareACL [");
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
        if (shareName != null) {
            builder.append("shareName=");
            builder.append(shareName);
            builder.append(", ");
        }

        if (permission != null) {
            builder.append("permission=");
            builder.append(permission);
        }
        builder.append("]");
        return builder.toString();
    }

    public ShareACL() {

    }

}
