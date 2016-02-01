/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "protection")
public class VirtualPoolProtectionParam {

    private VirtualPoolProtectionSnapshotsParam snapshots;

    public VirtualPoolProtectionParam() {
    }

    public VirtualPoolProtectionParam(
            VirtualPoolProtectionSnapshotsParam snapshots) {
        this.snapshots = snapshots;
    }

    /**
     * The protection snapshot settings for a virtual pool.
     * 
     */
    @XmlElement(name = "snapshots")
    public VirtualPoolProtectionSnapshotsParam getSnapshots() {
        return snapshots;
    }

    public void setSnapshots(VirtualPoolProtectionSnapshotsParam snapshots) {
        this.snapshots = snapshots;
    }

}
