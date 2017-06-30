/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.driversimulator;


import com.emc.storageos.storagedriver.storagecapabilities.CapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.CommonStorageCapabilities;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilitiesUtils;

/**
 * Utility class for storage driver simulator
 */
public class StorageDriverSimulatorUtils {

    public static CapabilityInstance getHostIOLimitsCapabilities(StorageCapabilities storageCapabilities) {
        // get hostio limits capability
        CapabilityInstance hostIOLimits = null;
        CommonStorageCapabilities commonCapabilities = storageCapabilities.getCommonCapabilitis();
        if (commonCapabilities != null) {
            hostIOLimits =StorageCapabilitiesUtils.getDataStorageServiceCapability(commonCapabilities, CapabilityDefinition.CapabilityUid.hostIOLimits);
        }
        return hostIOLimits;
    }
}
