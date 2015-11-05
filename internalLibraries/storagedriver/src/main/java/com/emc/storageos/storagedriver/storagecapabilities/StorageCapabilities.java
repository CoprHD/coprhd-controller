package com.emc.storageos.storagedriver.storagecapabilities;

/**
 * Describes all categories of storage capabilities.
 */
public class StorageCapabilities {

    private CommonStorageCapabilities commonCapabilitis;
    private CustomStorageCapabilities customCapabilities;

    public CommonStorageCapabilities getCommonCapabilitis() {
        return commonCapabilitis;
    }

    public void setCommonCapabilitis(CommonStorageCapabilities commonCapabilitis) {
        this.commonCapabilitis = commonCapabilitis;
    }

    public CustomStorageCapabilities getCustomCapabilities() {
        return customCapabilities;
    }

    public void setCustomCapabilities(CustomStorageCapabilities customCapabilities) {
        this.customCapabilities = customCapabilities;
    }
}
