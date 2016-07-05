/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.remotereplication;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.client.model.Volume;

import java.net.URI;

@Cf("RemoteReplicationPair")
public class RemoteReplicationPair extends DataObject {

    // Device nativeId of replication pair.
    private String nativeId;

    // If replication pair is part of replication group should be set to replication group URI, otherwise null.
    private URI replicationGroup;

    // Either direct replication set parent or replication set of the replication group parent.
    private URI replicationSet;
    private RemoteReplicationSet.ReplicationMode replicationMode;
    private RemoteReplicationSet.ReplicationState replicationState;
    private URI sourceElement;
    private URI targetElement;

    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    @RelationIndex(cf = "RelationIndex", type = RemoteReplicationGroup.class)
    @Name("replicationGroup")
    public URI getReplicationGroup() {
        return replicationGroup;
    }

    public void setReplicationGroup(URI replicationGroup) {
        this.replicationGroup = replicationGroup;
    }

    @RelationIndex(cf = "RelationIndex", type = RemoteReplicationSet.class)
    @Name("replicationSet")
    public URI getReplicationSet() {
        return replicationSet;
    }

    public void setReplicationSet(URI replicationSet) {
        this.replicationSet = replicationSet;
    }

    public RemoteReplicationSet.ReplicationMode getReplicationMode() {
        return replicationMode;
    }

    public void setReplicationMode(RemoteReplicationSet.ReplicationMode replicationMode) {
        this.replicationMode = replicationMode;
    }

    public RemoteReplicationSet.ReplicationState getReplicationState() {
        return replicationState;
    }

    public void setReplicationState(RemoteReplicationSet.ReplicationState replicationState) {
        this.replicationState = replicationState;
    }

    @RelationIndex(cf = "RelationIndex", type = Volume.class)
    @Name("sourceElement")
    public URI getSourceElement() {
        return sourceElement;
    }

    public void setSourceElement(URI sourceElement) {
        this.sourceElement = sourceElement;
    }

    @RelationIndex(cf = "RelationIndex", type = Volume.class)
    @Name("targetElement")
    public URI getTargetElement() {
        return targetElement;
    }

    public void setTargetElement(URI targetElement) {
        this.targetElement = targetElement;
    }
}
