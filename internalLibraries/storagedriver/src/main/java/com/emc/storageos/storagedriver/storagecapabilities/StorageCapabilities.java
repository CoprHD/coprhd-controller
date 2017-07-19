/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;

/**
 * Describes all categories of storage capabilities.
 */
public class StorageCapabilities {

    private CommonStorageCapabilities commonCapabilities;
    private CustomStorageCapabilities customCapabilities;

    public CommonStorageCapabilities getCommonCapabilities() {
        return commonCapabilities;
    }

    public void setCommonCapabilities(CommonStorageCapabilities commonCapabilities) {
        this.commonCapabilities = commonCapabilities;
    }

    public CustomStorageCapabilities getCustomCapabilities() {
        return customCapabilities;
    }

    public void setCustomCapabilities(CustomStorageCapabilities customCapabilities) {
        this.customCapabilities = customCapabilities;
    }
}
