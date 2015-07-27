/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "protection")
public class VirtualPoolProtectionParam {

    private VirtualPoolProtectionSnapshotsParam snapshots;

    public VirtualPoolProtectionParam() {}
    
    public VirtualPoolProtectionParam(
            VirtualPoolProtectionSnapshotsParam snapshots) {
        this.snapshots = snapshots;
    }

    /**
     * The protection snapshot settings for a virtual pool.
     * 
     * @valid none
     */
    @XmlElement(name="snapshots")
    public VirtualPoolProtectionSnapshotsParam getSnapshots() {
        return snapshots;
    }

    public void setSnapshots(VirtualPoolProtectionSnapshotsParam snapshots) {
        this.snapshots = snapshots;
    }
    
}
