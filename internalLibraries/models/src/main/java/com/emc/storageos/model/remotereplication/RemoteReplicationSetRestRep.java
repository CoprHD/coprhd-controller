package com.emc.storageos.model.remotereplication;


public class RemoteReplicationSetRestRep {

    // native id of replication set.
    private String nativeId;

    // Device label of this replication set
    private String deviceLabel;

    // If replication set is reachable.
    private Boolean reachable;

    // Type of storage systems in this replication set.
    private String storageSystemType;

    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public void setDeviceLabel(String deviceLabel) {
        this.deviceLabel = deviceLabel;
    }

    public Boolean getReachable() {
        return reachable;
    }

    public void setReachable(Boolean reachable) {
        this.reachable = reachable;
    }

    public String getStorageSystemType() {
        return storageSystemType;
    }

    public void setStorageSystemType(String storageSystemType) {
        this.storageSystemType = storageSystemType;
    }
}
