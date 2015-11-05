package com.emc.storageos.storagedriver.model;


public class VolumeConsistencyGroup {

    // Native ID. Type: input/output. If not supplied, should be set by driver.
    private String nativeId;

    // Storage system Id of this CG. Type: input/output.
    private String storageSystemId;

    public String getNativeId() {
        return nativeId;
    }

    // Snapshot name. Optional. Type: output.
    public String name;

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    public String getStorageSystemId() {
        return storageSystemId;
    }

    public void setStorageSystemId(String storageSystemId) {
        this.storageSystemId = storageSystemId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
