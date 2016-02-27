/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model;


public class StorageVolume extends StorageBlockObject {

    // storage system native id of this volume. Type: Input.
    private String storageSystemId;

    // storage pool native id of this volume. Type: Input.
    private String  storagePoolId;

    // Requested capacity of storage volume in bytes. Type: Input.
    private Long requestedCapacity;

    // Logical size of a storage volume on array in bytes. Type: Output.
    private Long provisionedCapacity;

    // Total amount of storage space allocated within the StoragePool in bytes. Type: Output.
    private Long allocatedCapacity;

    // thinVolumePreAllocate size in bytes. Type: Input.
    private Long thinVolumePreAllocationSize;

    // thin or thick volume type. Type: Input.
    Boolean thinlyProvisioned = false;

    public String getStorageSystemId() {
        return storageSystemId;
    }

    public void setStorageSystemId(String storageSystemId) {
        this.storageSystemId = storageSystemId;
    }

    public String getStoragePoolId() {
        return storagePoolId;
    }

    public void setStoragePoolId(String storagePoolId) {
        this.storagePoolId = storagePoolId;
    }

    public Long getRequestedCapacity() {
        return requestedCapacity;
    }

    public void setRequestedCapacity(Long requestedCapacity) {
        this.requestedCapacity = requestedCapacity;
    }

    public Long getProvisionedCapacity() {
        return provisionedCapacity;
    }

    public void setProvisionedCapacity(Long provisionedCapacity) {
        this.provisionedCapacity = provisionedCapacity;
    }

    public Long getAllocatedCapacity() {
        return allocatedCapacity;
    }

    public void setAllocatedCapacity(Long allocatedCapacity) {
        this.allocatedCapacity = allocatedCapacity;
    }

    public Long getThinVolumePreAllocationSize() {
        return thinVolumePreAllocationSize;
    }

    public void setThinVolumePreAllocationSize(Long thinVolumePreAllocationSize) {
        this.thinVolumePreAllocationSize = thinVolumePreAllocationSize;
    }

    public Boolean getThinlyProvisioned() {
        return thinlyProvisioned;
    }

    public void setThinlyProvisioned(Boolean thinlyProvisioned) {
        this.thinlyProvisioned = thinlyProvisioned;
    }

    @Override
    public boolean equals(Object volume) {
        if (volume != null && (volume instanceof StorageVolume) && storageSystemId.equals(((StorageVolume) volume).getStorageSystemId())) {
            if (getNativeId() != null && ((StorageVolume) volume).getNativeId() != null ) {
                // nativeId is not set before volume is created by driver. Need to account for this.
                if (getNativeId().equals(((StorageVolume) volume).getNativeId())) {
                    return true;
                } else {
                    return false;
                }
            } else {
                // if nativeId is not set we will compare displayName for these objects
                if (getDisplayName() != null && ((StorageVolume) volume).getDisplayName() != null &&
                        getDisplayName().equals(((StorageVolume) volume).getDisplayName())) {
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // We put volumes with the same display name in the same bucket. Different volumes with the same display name will fell
        // in the same hash code bucket.
        return ("StorageVolume-"+storageSystemId+"-"+getDisplayName()).hashCode();
    }

    @Override
    public String toString() {
        return "StorageVolume-"+storageSystemId+"-" + getDisplayName()+"-"+getNativeId();
    }

}
