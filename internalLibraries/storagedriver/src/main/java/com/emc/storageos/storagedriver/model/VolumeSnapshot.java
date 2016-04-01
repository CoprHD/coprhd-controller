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

    // snapSetId. Type: Input/Output.
    // Should be set to the same value for all consistency group  snapshots taken at the same time.
    private String snapSetId;


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

    public String getSnapSetId() {
        return snapSetId;
    }

    public void setSnapSetId(String snapSetId) {
        this.snapSetId = snapSetId;
    }

    @Override
    public String toString() {
        return getNativeId();
    }
}
