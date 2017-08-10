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

@XmlRootElement(name = "bulk_remotereplicationsets")
public class RemoteReplicationSetBulkRep extends BulkRestRep {
    private List<RemoteReplicationSetRestRep> sets;

    /**
     * List of remote replication sets
     * 
     * @return List of remote replication sets
     */
    @XmlElement(name = "remotereplicationset")
    public List<RemoteReplicationSetRestRep> getSets() {
        if (sets == null) {
            sets = new ArrayList<RemoteReplicationSetRestRep>();
        }
        return sets;
    }

    public void setSets(List<RemoteReplicationSetRestRep> sets) {
        this.sets = sets;
    }

    public RemoteReplicationSetBulkRep() {
    }

    public RemoteReplicationSetBulkRep(List<RemoteReplicationSetRestRep> sets) {
        this.sets = sets;
    }
}
