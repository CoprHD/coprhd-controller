/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model;


public class VolumeSnapshot extends StorageBlockObject {

    // volume native Id this snapshot is associated with. Type: Input.
    private String parentId;

    // storage system native id of this snapshot. Type: Input.
    private String storageSystemId;

    // use consistencyGroup for snapshot snapset.
    // snapSetId. Type: Input/Output.
    // Should be set to the same value for all consistency group  snapshots taken at the same time.
    @Deprecated
    private String snapSetId;

    // Logical size of a snap on array in bytes. Type: Output.
    private Long provisionedCapacity = 0L;

    // Total amount of storage space allocated within the StoragePool in bytes. Type: Output.
    private Long allocatedCapacity = 0L;

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

    public String getSnapSetId() {
        return snapSetId;
    }

    public void setSnapSetId(String snapSetId) {
        this.snapSetId = snapSetId;
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

    @Override
    public String toString() {
        return getNativeId();
    }
}

