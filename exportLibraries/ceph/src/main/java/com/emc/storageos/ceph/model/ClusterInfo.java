/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.ceph.model;

public class ClusterInfo {

    // Unique Ceph cluster identifier (guid)
    // the established name came from obsolete meaning File System ID
    private String fsid;

    // Total cluster capacity in kb
    private long kb;

    // Free cluster capacity in kb
    private long kbAvail;

    public String getFsid() {
        return fsid;
    }

    public void setFsid(final String fsid) {
        this.fsid = fsid;
    }

    public long getKb() {
        return kb;
    }

    public void setKb(long kb) {
        this.kb = kb;
    }

    public long getKbAvail() {
        return kbAvail;
    }

    public void setKbAvail(long kbAvail) {
        this.kbAvail = kbAvail;
    }
}
