/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("FileNfsACL")
public class FileNfsACL extends FileACL {

    protected URI fileSystemId;
    protected URI snapshotId;
    protected URI fileExportRuleId;
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

    @RelationIndex(cf = "RelationIndex", type = FileExportRule.class)
    @Name("fileExportRuleId")
    public URI getFileExportRuleId() {
        return fileExportRuleId;
    }

    public void setFileExportRuleId(URI fileExportRuleId) {
        this.fileExportRuleId = fileExportRuleId;
        calculateACLIndex();
        setChanged("fileExportRuleId");
    }

    @Name("opType")
    public String getOpType() {
        return opType;
    }

    public void setOpType(String opType) {
        this.opType = opType;
        setChanged("opType");
    }

    @Override
    public void calculateACLIndex() {

        String userOrGroup = this.user == null ? this.group : this.user;
        StringBuffer aclIndexBuffer = new StringBuffer();

        if (this.fileSystemPath != null && userOrGroup != null) {
            if (this.fileSystemId != null) {
                aclIndexBuffer.append(this.fileSystemId.toString())
                        .append(this.fileSystemPath)
                        .append(this.domain == null ? "" : this.domain)
                        .append(userOrGroup);
                this.setfileSystemExportACLIndex(aclIndexBuffer.toString());
            }

            if (this.snapshotId != null) {
                aclIndexBuffer.append(this.snapshotId.toString())
                        .append(this.fileSystemPath)
                        .append(this.domain == null ? "" : this.domain)
                        .append(userOrGroup);
                this.setsnapshotExportACLIndex(aclIndexBuffer.toString());
            }

        }

    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("FileNfsACL [");
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
        if (fileSystemExportACLIndex != null) {
            builder.append("fileSystemExportACLIndex=");
            builder.append(fileSystemExportACLIndex);
            builder.append(", ");
        }
        if (snapshotExportACLIndex != null) {
            builder.append("snapshotExportACLIndex=");
            builder.append(snapshotExportACLIndex);
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
        if (fileSystemPath != null) {
            builder.append("fileSystemPath=");
            builder.append(fileSystemPath);
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
