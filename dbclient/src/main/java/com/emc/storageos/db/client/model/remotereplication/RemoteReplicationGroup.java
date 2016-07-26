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

    // If replication group is reachable
    private Boolean reachable;

    private String displayName;

    // source storage system of this group
    private String sourceSystemGuid;

    // target storage system of this group
    private String targetSystemGuid;

    // replication mode of this group
    String replicationMode;

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
        setChanged("nativeId");
    }

    @Name("reachable")
    public Boolean getReachableStatus() {
        return reachable == null ? false : reachable;
    }

    public void setReachableStatus(final Boolean reachable) {
        this.reachable = reachable;
        setChanged("reachable");
    }

    @Name("displayName")
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        setChanged("displayName");
    }

    @Name("sourceSystemGuid")
    public String getSourceSystemGuid() {
        return sourceSystemGuid;
    }

    public void setSourceSystemGuid(String sourceSystemGuid) {
        this.sourceSystemGuid = sourceSystemGuid;
        setChanged("sourceSystemGuid");
    }

    @Name("targetSystemGuid")
    public String getTargetSystemGuid() {
        return targetSystemGuid;
    }

    public void setTargetSystemGuid(String targetSystemGuid) {
        this.targetSystemGuid = targetSystemGuid;
        setChanged("targetSystemGuid");
    }

    @Name("replicationMode")
    public String getReplicationMode() {
        return replicationMode;
    }

    public void setReplicationMode(String replicationMode) {
        this.replicationMode = replicationMode;
        setChanged("replicationMode");
    }

    @Name("replicationState")
    public RemoteReplicationSet.ReplicationState getReplicationState() {
        return replicationState;
    }

    public void setReplicationState(RemoteReplicationSet.ReplicationState replicationState) {
        this.replicationState = replicationState;
        setChanged("replicationState");
    }

    @RelationIndex(cf = "RelationIndex", type = RemoteReplicationSet.class)
    @Name("replicationSet")
    public URI getReplicationSet() {
        return replicationSet;
    }

    public void setReplicationSet(URI replicationSet) {
        this.replicationSet = replicationSet;
        setChanged("replicationSet");
    }

}
