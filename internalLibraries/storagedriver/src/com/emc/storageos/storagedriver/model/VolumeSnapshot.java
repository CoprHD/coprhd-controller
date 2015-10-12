package com.emc.storageos.storagedriver.model;


public class VolumeSnapshot extends StorageObject {

    // volume Id this snapshot is associated with. Type: Input.
    private String parentId;

    // timestamp. Type: Output.
    private String timestamp;


    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
