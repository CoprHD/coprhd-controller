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

    @Override
    public boolean equals(Object clone) {
        if (clone != null && (clone instanceof VolumeClone) && storageSystemId.equals(((VolumeClone) clone).getStorageSystemId())) {
            if (getNativeId() != null && ((VolumeClone) clone).getNativeId() != null ) {
                // nativeId is not set before clone is created by driver. Need to account for this.
                if (getNativeId().equals(((VolumeClone) clone).getNativeId())) {
                    return true;
                } else {
                    return false;
                }
            } else {
                // if nativeId is not set we will compare parent source volumes for clones
                if (getParentId() != null && ((VolumeClone) clone).getParentId() != null &&
                        getParentId().equals(((VolumeClone) clone).getParentId())) {
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // We put clones for the same parent in the same bucket.
        // Different clones with the same parent will fell
        // in the same hash code bucket.
        return ("VolumeClone-"+storageSystemId+"-"+getParentId()).hashCode();
    }

    @Override
    public String toString() {
        return "VolumeClone-"+storageSystemId+"-" + getParentId()+"-"+getNativeId();
    }
}
