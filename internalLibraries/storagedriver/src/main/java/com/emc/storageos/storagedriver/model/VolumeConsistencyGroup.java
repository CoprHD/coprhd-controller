/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model;


public class VolumeConsistencyGroup {

    // Native ID. Type: input/output. If not supplied, should be set by driver.
    private String nativeId;

    // Storage system Id of this CG. Type: input/output.
    private String storageSystemId;

    // Display name of this object. Type: Input.
    private String displayName;

    // Device label of this object. Type: input/output. If not supplied, should be set by driver.
    private String deviceLabel;


    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    public String getStorageSystemId() {
        return storageSystemId;
    }

    public void setStorageSystemId(String storageSystemId) {
        this.storageSystemId = storageSystemId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public void setDeviceLabel(String deviceLabel) {
        this.deviceLabel = deviceLabel;
    }
}
