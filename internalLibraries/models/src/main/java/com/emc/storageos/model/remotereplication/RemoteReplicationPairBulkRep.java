/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.remotereplication;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_remotereplicationpairs")
public class RemoteReplicationPairBulkRep extends BulkRestRep {
    private List<RemoteReplicationPairRestRep> pairs;

    /**
     * List of remote replication pairs
     * 
     * @return List of remote replication pairs
     */
    @XmlElement(name = "remotereplicationpair")
    public List<RemoteReplicationPairRestRep> getPairs() {
        if (pairs == null) {
            pairs = new ArrayList<RemoteReplicationPairRestRep>();
        }
        return pairs;
    }

    public void setPairs(List<RemoteReplicationPairRestRep> pairs) {
        this.pairs = pairs;
    }

    public RemoteReplicationPairBulkRep() {
    }

    public RemoteReplicationPairBulkRep(List<RemoteReplicationPairRestRep> pairs) {
        this.pairs = pairs;
    }
}
