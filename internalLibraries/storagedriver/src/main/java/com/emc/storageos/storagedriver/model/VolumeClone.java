/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model;


public class VolumeClone extends StorageBlockObject {

    // parent volume id. Type: Input.
    private String parentId;

    // storage system of this volume. Type: Input.
    private String storageSystemId;

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

    public String getStorageSystemId() {
        return storageSystemId;
    }

    public void setStorageSystemId(String storageSystemId) {
        this.storageSystemId = storageSystemId;
    }

    public ReplicationState getReplicationState() {
        return replicationState;
    }

    public void setReplicationState(ReplicationState replicationState) {
        this.replicationState = replicationState;
    }
}
