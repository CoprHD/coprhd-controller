/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model;


public class VolumeSnapshot extends StorageBlockObject {

    // volume native Id this snapshot is associated with. Type: Input.
    private String parentId;

    // storage system native id of this snapshot. Type: Input.
    private String storageSystemId;

    // timestamp. Type: Input/Output.
    // Should be set to the same value for all consistency group  snapshots taken at the same time.
    private String timestamp;


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

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object snapshot) {
        if (snapshot != null && (snapshot instanceof VolumeSnapshot) && storageSystemId.equals(((VolumeSnapshot) snapshot).getStorageSystemId())) {
            if (getNativeId() != null && ((VolumeSnapshot) snapshot).getNativeId() != null ) {
                // nativeId is not set before snapshot is created by driver. Need to account for this.
                if (getNativeId().equals(((VolumeSnapshot) snapshot).getNativeId())) {
                    return true;
                } else {
                    return false;
                }
            } else {
                // if nativeId is not set we will compare parent source volumes for snaps
                if (getParentId() != null && ((VolumeSnapshot) snapshot).getParentId() != null &&
                        getParentId().equals(((VolumeSnapshot) snapshot).getParentId())) {
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
        // We put snaps for the same parent in the same bucket
        return ("VolumeSnapshot-"+storageSystemId+"-"+getParentId()).hashCode();
    }

    @Override
    public String toString() {
        return "VolumeSnapshot-"+storageSystemId+"-" + getParentId()+"-"+getNativeId();
    }
}
