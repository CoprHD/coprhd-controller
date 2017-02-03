/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "nfs_acls")
public class NfsACLs implements Serializable {

    private static final long serialVersionUID = 1780598964262028652L;

    private URI fileSystemId;
    private URI snapshotId;
    private List<NfsACL> nfsACLs;

    public NfsACLs() {
    }

    @XmlElement(name = "nfs_acl")
    public List<NfsACL> getNfsACLs() {
        return nfsACLs;
    }

    @XmlElement(name = "file_system_id")
    public URI getFileSystemId() {
        return fileSystemId;
    }

    public void setFileSystemId(URI fileSystemId) {
        this.fileSystemId = fileSystemId;
    }

    @XmlElement(name = "snapshot_id")
    public URI getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(URI snapshotId) {
        this.snapshotId = snapshotId;
    }

    public void setNfsACLs(List<NfsACL> nfsACLs) {
        this.nfsACLs = nfsACLs;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("NfsACLs [");
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
        if (nfsACLs != null) {
            builder.append("nfsACLs=");
            builder.append(nfsACLs);
        }
        builder.append("]");
        return builder.toString();
    }
}
