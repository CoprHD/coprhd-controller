package com.emc.storageos.storagedriver.model;


public class VolumeMirror extends StorageObject {

    // Volume Id this mirror is associated with. Type: Input.
    private String parentId;

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

    public SynchronizationState getSyncState() {
        return syncState;
    }

    public void setSyncState(SynchronizationState syncState) {
        this.syncState = syncState;
    }
}
