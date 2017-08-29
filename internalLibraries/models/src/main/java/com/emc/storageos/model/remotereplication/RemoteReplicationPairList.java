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
 * Used for remote replication pair listing
 */
@XmlRootElement(name = "remote_replication_pairs")
public class RemoteReplicationPairList {
    private List<NamedRelatedResourceRep> remoteReplicationPairs;

    public RemoteReplicationPairList() {
    }

    public RemoteReplicationPairList(List<NamedRelatedResourceRep> remoteReplicationPairs) {
        this.remoteReplicationPairs = remoteReplicationPairs;
    }

    /**
     * Get remoteReplicationPair instances
     * @return remoteReplicationPairps.
     */
    @XmlElement(name = "remote_replication_pair")
    public List<NamedRelatedResourceRep> getRemoteReplicationPairs() {
        if (remoteReplicationPairs == null) {
            remoteReplicationPairs = new ArrayList<NamedRelatedResourceRep>();
        }
        return remoteReplicationPairs;
    }

    public void setRemoteReplicationPairs(List<NamedRelatedResourceRep> remoteReplicationPairs) {
        this.remoteReplicationPairs = remoteReplicationPairs;
    }
}