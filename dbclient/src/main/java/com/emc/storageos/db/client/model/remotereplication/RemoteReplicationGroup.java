/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.remotereplication;


import java.net.URI;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.RelationIndex;

@Cf("RemoteReplicationGroup")
public class RemoteReplicationGroup extends DataObject {

    // native id of this group
    private String nativeId;

    private String displayName;

    // source storage system of this group
    private String sourceSystemGuid;

    // target storage system of this group
    private String targetSystemGuid;

    // replication mode of this group
    RemoteReplicationSet.ReplicationMode replicationMode;

    // replication state of this group
    RemoteReplicationSet.ReplicationState replicationState;

    // parent replication set
    URI replicationSet;

    @Name("nativeId")
    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    @Name("displayName")
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Name("sourceSystemGuid")
    public String getSourceSystemGuid() {
        return sourceSystemGuid;
    }

    public void setSourceSystemGuid(String sourceSystemGuid) {
        this.sourceSystemGuid = sourceSystemGuid;
    }

    @Name("targetSystemGuid")
    public String getTargetSystemGuid() {
        return targetSystemGuid;
    }

    public void setTargetSystemGuid(String targetSystemGuid) {
        this.targetSystemGuid = targetSystemGuid;
    }

    @Name("replicationMode")
    public RemoteReplicationSet.ReplicationMode getReplicationMode() {
        return replicationMode;
    }

    public void setReplicationMode(RemoteReplicationSet.ReplicationMode replicationMode) {
        this.replicationMode = replicationMode;
    }

    @Name("replicationState")
    public RemoteReplicationSet.ReplicationState getReplicationState() {
        return replicationState;
    }

    public void setReplicationState(RemoteReplicationSet.ReplicationState replicationState) {
        this.replicationState = replicationState;
    }

    @RelationIndex(cf = "RelationIndex", type = RemoteReplicationSet.class)
    @Name("replicationSet")
    public URI getReplicationSet() {
        return replicationSet;
    }

    public void setReplicationSet(URI replicationSet) {
        this.replicationSet = replicationSet;
    }

}
