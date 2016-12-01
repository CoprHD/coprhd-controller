package com.emc.storageos.model.remotereplication;


public class RemoteReplicationGroupRestRep {

    // native id of this group
    private String nativeId;

    // If replication group is reachable.
    private Boolean reachable;

    // Device label of this replication group.
    private String deviceLabel;

    // Type of storage systems in this replication group.
    private String storageSystemType;

    // Display name of this replication group (whem provisioned by the systemt).
    private String displayName;

    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    public Boolean getReachable() {
        return reachable;
    }

    public void setReachable(Boolean reachable) {
        this.reachable = reachable;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public void setDeviceLabel(String deviceLabel) {
        this.deviceLabel = deviceLabel;
    }

    public String getStorageSystemType() {
        return storageSystemType;
    }

    public void setStorageSystemType(String storageSystemType) {
        this.storageSystemType = storageSystemType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
