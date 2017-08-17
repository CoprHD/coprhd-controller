/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.remotereplication;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Used for remote replication group listing
 */
@XmlRootElement(name = "remote_replication_groups")
public class RemoteReplicationGroupList {
    private List<NamedRelatedResourceRep> remoteReplicationGroups;

    public RemoteReplicationGroupList() {
    }

    public RemoteReplicationGroupList(List<NamedRelatedResourceRep> remoteReplicationGroups) {
        this.remoteReplicationGroups = remoteReplicationGroups;
    }

    /**
     * Get remoteReplicationGroup instances
     * @return remoteReplicationGroups.
     */
    @XmlElement(name = "remote_replication_group")
    public List<NamedRelatedResourceRep> getRemoteReplicationGroups() {
        if (remoteReplicationGroups == null) {
            remoteReplicationGroups = new ArrayList<NamedRelatedResourceRep>();
        }
        return remoteReplicationGroups;
    }

    public void setRemoteReplicationGroups(List<NamedRelatedResourceRep> remoteReplicationGroups) {
        this.remoteReplicationGroups = remoteReplicationGroups;
    }
}