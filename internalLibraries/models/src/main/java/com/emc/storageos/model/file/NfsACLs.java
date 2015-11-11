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

    /*
     * response data attributes.
     */

    private static final long serialVersionUID = -5805098581764691677L;

    private URI fileSystemId;
    private URI snapshotId;
    private List<NfsACL> nfsACLs;

    @XmlElement(name = "fsID")
    public URI getFileSystemId() {
        return fileSystemId;
    }

    public void setFileSystemId(URI fileSystemId) {
        this.fileSystemId = fileSystemId;
    }

    @XmlElement(name = "snapShotID")
    public URI getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(URI snapshotId) {
        this.snapshotId = snapshotId;
    }

    @XmlElement(name = "nfs_acl")
    public List<NfsACL> getNfsACLs() {
        return nfsACLs;
    }

    public void setNfsACLs(List<NfsACL> nfsACLs) {
        this.nfsACLs = nfsACLs;
    }

}
