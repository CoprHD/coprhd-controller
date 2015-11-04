/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * NFSShareACL will contain the details of NFS ACL on File Systems and Snapshots.
 * It will hold information about the fileSystemId, snapshotId mapped to NFSShareACL
 * 
 * @author sauraa
 * 
 */

@Cf("NFSShareACL")
public class NFSShareACL extends FileACL {

    protected URI fileSystemId;
    protected URI snapshotId;

    @RelationIndex(cf = "RelationIndex", type = Snapshot.class)
    @Name("snapshotId")
    public URI getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(URI snapshotId) {
        this.snapshotId = snapshotId;
        calculateNfsACLIndex();
        setChanged("snapshotId");
    }

    @RelationIndex(cf = "RelationIndex", type = FileShare.class)
    @Name("fileSystemId")
    public URI getFileSystemId() {
        return fileSystemId;
    }

    public void setFileSystemId(URI fileSystemId) {
        this.fileSystemId = fileSystemId;
        calculateNfsACLIndex();
        setChanged("fileSystemId");
    }

    @Override
    public void calculateNfsACLIndex() {

        StringBuffer aclIndexBuffer = new StringBuffer();

        if (this.fileSystemId != null && this.user != null) {
            aclIndexBuffer.append(this.fileSystemId.toString())
                    .append(this.fileSystemPath)
                    .append(this.domain == null ? "" : this.domain)
                    .append(this.user);

            this.setFileSystemNfsACLIndex(aclIndexBuffer.toString());
        }

        if (this.snapshotId != null && this.user != null) {
            aclIndexBuffer.append(this.snapshotId.toString())
                    .append(this.fileSystemPath)
                    .append(this.domain == null ? "" : this.domain)
                    .append(this.user);
            this.setSnapshotNfsACLIndex(aclIndexBuffer.toString());
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
        if (fileSystemNfsACLIndex != null) {
            builder.append("fileSystemNfsACLIndex=");
            builder.append(fileSystemNfsACLIndex);
            builder.append(", ");
        }
        if (snapshotNfsACLIndex != null) {
            builder.append("snapshotNfsACLIndex=");
            builder.append(snapshotNfsACLIndex);
            builder.append(", ");
        }
        if (user != null) {
            builder.append("user=");
            builder.append(user);
            builder.append(", ");
        }
        if (type != null) {
            builder.append("type=");
            builder.append(type);
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
        if (permissions != null) {
            builder.append("permissions=");
            builder.append(permissions);
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
