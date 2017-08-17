/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.remotereplication;


import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

/**
 * Used for remote replication set listing
 */
@XmlRootElement(name = "remote_replication_sets")
public class RemoteReplicationSetList {
    private List<NamedRelatedResourceRep> remoteReplicationSets;

    public RemoteReplicationSetList() {
    }

    public RemoteReplicationSetList(List<NamedRelatedResourceRep> remoteReplicationSets) {
        this.remoteReplicationSets = remoteReplicationSets;
    }

    /**
     * Get remoteReplicationSet instances
     * @return remoteReplicationSets.
     */
    @XmlElement(name = "remote_replication_set")
    public List<NamedRelatedResourceRep> getRemoteReplicationSets() {
        if (remoteReplicationSets == null) {
            remoteReplicationSets = new ArrayList<NamedRelatedResourceRep>();
        }
        return remoteReplicationSets;
    }

    public void setRemoteReplicationSets(List<NamedRelatedResourceRep> remoteReplicationSets) {
        this.remoteReplicationSets = remoteReplicationSets;
    }
}
