/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model;


public class VolumeSnapshot extends StorageBlockObject {

    // volume Id this snapshot is associated with. Type: Input.
    private String parentId;

    // storage system of this volume. Type: Input.
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
}
