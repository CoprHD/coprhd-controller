package com.emc.storageos.storagedriver.model;

import com.emc.storageos.storagedriver.CapabilityInstance;

import java.util.List;


public class StorageVolume extends StorageObject {

    // storage system of this volume. Type: Input.
    private String storageSystemId;

    // storage pool of this volume. Type: Input.
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

    // Capabilities associated with volumes. Type: Output.
    private List<CapabilityInstance> capabilities;

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

    public List<CapabilityInstance> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<CapabilityInstance> capabilities) {
        this.capabilities = capabilities;
    }
}
