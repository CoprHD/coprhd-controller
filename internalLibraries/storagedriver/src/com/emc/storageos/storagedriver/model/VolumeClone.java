package com.emc.storageos.storagedriver.model;


public class VolumeClone extends StorageObject {

    // parent volume id. Type: Input.
    private String parentId;

    // replication state of this clone. Type: Output.
    ReplicationState replicationState;

    public static enum ReplicationState {
        UNKNOWN, SYNCHRONIZED, CREATED, RESYNCED, INACTIVE, DETACHED, RESTORED;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public ReplicationState getReplicationState() {
        return replicationState;
    }

    public void setReplicationState(ReplicationState replicationState) {
        this.replicationState = replicationState;
    }
}
