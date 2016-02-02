/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model;


public class VolumeMirror extends StorageBlockObject {

    // Volume Id this mirror is associated with. Type: Input.
    private String parentId;

    // storage system of this volume. Type: Input.
    private String storageSystemId;

    // Synchronization state. Type: Output.
    private SynchronizationState syncState;


    public static enum SynchronizationState {
        UNKNOWN, RESYNCHRONIZING, SYNCHRONIZED, FRACTURED, COPYINPROGRESS;
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

    public SynchronizationState getSyncState() {
        return syncState;
    }

    public void setSyncState(SynchronizationState syncState) {
        this.syncState = syncState;
    }
}
