package com.emc.storageos.ceph.model;

public class ClusterInfo {

    private String fsid;
    private long kb;
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
