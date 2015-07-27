/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
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

/**
 * Snapshot options for Virtual Pool
 */
public class VirtualPoolProtectionSnapshotsParam {
    
    public final static int MAX_DISABLED  =  0;
   
    private Integer maxSnapshots;

    public VirtualPoolProtectionSnapshotsParam() {}
    
    public VirtualPoolProtectionSnapshotsParam(Integer maxSnapshots) {
        this.maxSnapshots = maxSnapshots;
    }

    /**
     * The maximum snapshots.
     * 
     * @valid none
     */
    @XmlElement(name="max_native_snapshots")
    public Integer getMaxSnapshots() {
        return maxSnapshots;
    }

    public void setMaxSnapshots(Integer maxSnapshots) {
        this.maxSnapshots = maxSnapshots;
    }

}
