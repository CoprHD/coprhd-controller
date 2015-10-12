package com.emc.storageos.storagedriver.model;

public class StorageObject {

    // Device native ID for this storage object. Type: input/output. If not supplied, should be set by driver.
    private String nativeId;

    // Device label of this object. Type: input/output. If not supplied, should be set by driver.
    private String displayName;

    // Access status. Should be set by driver.
    private AccessStatus accessStatus;

    public static enum AccessStatus {
        READ_ONLY,
        WRITE_ONLY,
        READ_WRITE,
        NOT_READY

    }

    // Consistency group for this storage object. Type: input/output.
    private String consistencyGroup;

    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public AccessStatus getAccessStatus() {
        return accessStatus;
    }

    public void setAccessStatus(AccessStatus accessStatus) {
        this.accessStatus = accessStatus;
    }

    public String getConsistencyGroup() {
        return consistencyGroup;
    }

    public void setConsistencyGroup(String consistencyGroup) {
        this.consistencyGroup = consistencyGroup;
    }
}
