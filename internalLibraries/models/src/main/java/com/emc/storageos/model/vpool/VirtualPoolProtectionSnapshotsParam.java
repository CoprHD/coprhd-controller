/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;

/**
 * Snapshot options for Virtual Pool
 */
public class VirtualPoolProtectionSnapshotsParam {

    public final static int MAX_DISABLED = 0;

    private Integer maxSnapshots;

    public VirtualPoolProtectionSnapshotsParam() {
    }

    public VirtualPoolProtectionSnapshotsParam(Integer maxSnapshots) {
        this.maxSnapshots = maxSnapshots;
    }

    /**
     * The maximum snapshots.
     * 
     */
    @XmlElement(name = "max_native_snapshots")
    public Integer getMaxSnapshots() {
        return maxSnapshots;
    }

    public void setMaxSnapshots(Integer maxSnapshots) {
        this.maxSnapshots = maxSnapshots;
    }

}
