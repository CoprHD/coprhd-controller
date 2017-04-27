package com.emc.storageos.model.remotereplication;


import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Used for remote replication set listing
 */
@XmlRootElement(name = "remote_replication_sets")
public class RemoteReplicationSetList {
    private List<RemoteReplicationSetRestRep> remoteReplicationSets;

    public RemoteReplicationSetList() {
    }

    public RemoteReplicationSetList(List<RemoteReplicationSetRestRep> remoteReplicationSets) {
        this.remoteReplicationSets = remoteReplicationSets;
    }

    /**
     * Get remoteReplicationSet instances
     * @return remoteReplicationSets.
     */
    @XmlElement(name = "remote_replication_set")
    public List<RemoteReplicationSetRestRep> getRemoteReplicationSets() {
        if (remoteReplicationSets == null) {
            remoteReplicationSets = new ArrayList<RemoteReplicationSetRestRep>();
        }
        return remoteReplicationSets;
    }

    public void setRemoteReplicationSets(List<RemoteReplicationSetRestRep> remoteReplicationSets) {
        this.remoteReplicationSets = remoteReplicationSets;
    }
}
