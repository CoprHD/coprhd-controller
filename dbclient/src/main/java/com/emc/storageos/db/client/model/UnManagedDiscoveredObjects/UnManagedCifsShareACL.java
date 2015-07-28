/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.UnManagedDiscoveredObjects;

import java.net.URI;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.ShareACL;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.RelationIndex;

@Cf("UnManagedCifsShareACL")
public class UnManagedCifsShareACL extends ShareACL {

    protected URI fileSystemId;
    protected URI snapshotId;
    protected String opType;

    protected String fsCifsShareIndex;

    private String nativeGuid;

    @RelationIndex(cf = "RelationIndex", type = Snapshot.class)
    @Name("snapshotId")
    public URI getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(URI snapshotId) {
        this.snapshotId = snapshotId;
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

    @AlternateId("fileShareNativeGuidTable")
    @Name("nativeGuid")
    public String getNativeGuid() {
        return nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        this.nativeGuid = nativeGuid;
        setChanged("nativeGuid");
    }

    @Override
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

        }

    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("UnManagedCifsShareACL [");
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
        if (opType != null) {
            builder.append("opType=");
            builder.append(opType);
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
        if (deviceSharePath != null) {
            builder.append("deviceSharePath=");
            builder.append(deviceSharePath);
            builder.append(", ");
        }
        if (_id != null) {
            builder.append("_id=");
            builder.append(_id);
        }
        builder.append("]");
        return builder.toString();
    }
}
