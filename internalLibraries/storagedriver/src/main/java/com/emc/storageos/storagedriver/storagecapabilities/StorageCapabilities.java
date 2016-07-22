/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;

import java.util.List;

/**
 * Describes all categories of storage capabilities.
 */
public class StorageCapabilities {

    private List<CapabilityInstance> commonCapabilities;
    private List<CapabilityInstance> customCapabilities;

    public List<CapabilityInstance> getCommonCapabilitis() {
        return commonCapabilities;
    }

    public void setCommonCapabilitis(List<CapabilityInstance> commonCapabilities) {
        this.commonCapabilities = commonCapabilities;
    }

    public List<CapabilityInstance> getCustomCapabilities() {
        return customCapabilities;
    }

    public void setCustomCapabilities(List<CapabilityInstance> customCapabilities) {
        this.customCapabilities = customCapabilities;
    }
}
