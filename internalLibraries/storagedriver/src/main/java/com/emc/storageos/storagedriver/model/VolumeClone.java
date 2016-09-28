/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model;


public class VolumeClone extends StorageVolume {

    // parent volume id. Type: Input.
    private String parentId;

    // storage system of this volume. Type: Input.
    private String storageSystemId;

    // replication state of this clone. Type: Output.
    ReplicationState replicationState = ReplicationState.UNKNOWN;
    
    // sourcetype of the clone.
    SourceType sourceType = SourceType.UNKNOWN;

    public static enum ReplicationState {
        UNKNOWN, SYNCHRONIZED, CREATED, RESYNCED, INACTIVE, DETACHED, RESTORED;
    }
    
    public static enum SourceType  {
    	UNKNOWN, VOLUME, SNAPSHOT;
    }
    
    public SourceType getSourceType(){
    	return sourceType;
    }
    
    public String getParentId() {
        return parentId;
    }
    
    public void setSourceType(SourceType sourceType){
    	this.sourceType = sourceType;
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

    @Override
    public String toString() {
        return getNativeId();
    }
}
