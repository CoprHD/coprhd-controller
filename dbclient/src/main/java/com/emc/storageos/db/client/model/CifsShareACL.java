/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("CifsShareACL")
public class CifsShareACL extends ShareACL {

    protected URI fileSystemId;
    protected URI snapshotId;
    protected String opType;

    @RelationIndex(cf = "RelationIndex", type = Snapshot.class)
    @Name("snapshotId")
    public URI getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(URI snapshotId) {
        this.snapshotId = snapshotId;
        calculateACLIndex();
        setChanged("snapshotId");
    }

    @RelationIndex(cf = "RelationIndex", type = FileShare.class)
    @Name("fileSystemId")
    public URI getFileSystemId() {
        return fileSystemId;
    }

    public void setFileSystemId(URI fileSystemId) {
        this.fileSystemId = fileSystemId;
        calculateACLIndex();
        setChanged("fileSystemId");
    }

    @Name("opType")
    public String getOpType() {
        return opType;
    }

    public void setOpType(String opType) {
        this.opType = opType;
        setChanged("opType");
    }

    public void calculateACLIndex() {

        String userOrGroup = this.user == null ? this.group : this.user;
        StringBuffer aclIndexBuffer = new StringBuffer();

        if (this.shareName != null && userOrGroup != null) {
            if (this.fileSystemId != null) {
                aclIndexBuffer.append(this.fileSystemId.toString())
                        .append(this.shareName)
                        .append(this.domain == null ? "" : this.domain)
                        .append(userOrGroup);
                this.setFileSystemShareACLIndex(aclIndexBuffer.toString());
            }

            if (this.snapshotId != null) {
                aclIndexBuffer.append(this.snapshotId.toString())
                        .append(this.shareName)
                        .append(this.domain == null ? "" : this.domain)
                        .append(userOrGroup);
                this.setSnapshotShareACLIndex(aclIndexBuffer.toString());
            }

        }

    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("CifsShareACL [");
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
        if (fileSystemShareACLIndex != null) {
            builder.append("fileSystemShareACLIndex=");
            builder.append(fileSystemShareACLIndex);
            builder.append(", ");
        }
        if (snapshotShareACLIndex != null) {
            builder.append("snapshotShareACLIndex=");
            builder.append(snapshotShareACLIndex);
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
        if (domain != null) {
            builder.append("domain=");
            builder.append(domain);
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
            builder.append(", ");
        }
        if (_id != null) {
            builder.append("_id=");
            builder.append(_id);
            builder.append(", ");
        }
        if (_inactive != null) {
            builder.append("_inactive=");
            builder.append(_inactive);
        }
        builder.append("]");
        return builder.toString();
    }

}
