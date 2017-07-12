/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.restvmax.vmax.type;

public class GetStorageGroupResultType {
    private StorageGroupType storageGroup;

    public StorageGroupType getStorageGroup() {
        return storageGroup;
    }

    public void setStorageGroup(StorageGroupType storageGroup) {
        this.storageGroup = storageGroup;
    }
}
