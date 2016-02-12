/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;

import java.util.List;

/**
 * This class describes set of custom storage capabilities of storage
 */

public class CustomStorageCapabilities {

    private List<CapabilityInstance> customCapabilities;

    public List<CapabilityInstance> getCustomCapabilities() {
        return customCapabilities;
    }

    public void setCustomCapabilities(List<CapabilityInstance> customCapabilities) {
        this.customCapabilities = customCapabilities;
    }
}
