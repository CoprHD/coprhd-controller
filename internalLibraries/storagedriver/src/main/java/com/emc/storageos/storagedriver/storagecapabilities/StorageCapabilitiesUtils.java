/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class with convenience methods for storage capabilities
 */
public class StorageCapabilitiesUtils {

    /**
     * Add new DataStorageServiceOption for  the passed common capability instances
     *
     * @param storageCapabilities common storage capabilities container
     * @param capabilities capability instances for a new data storage service option
     */
    public static void addDataStorageServiceOption(CommonStorageCapabilities storageCapabilities, List<CapabilityInstance> capabilities) {

        // Get the data storage service options for the common capabilities.
        // If null, create it and set it.
        List<DataStorageServiceOption> dataStorageSvcOptions = storageCapabilities.getDataStorage();
        if (dataStorageSvcOptions == null) {
            dataStorageSvcOptions = new ArrayList<>();
            storageCapabilities.setDataStorage(dataStorageSvcOptions);
        }

        // Create a new data storage service option for the passed capabilities
        // and add it to the list.
        DataStorageServiceOption dataStorageSvcOption = new DataStorageServiceOption(capabilities);
        dataStorageSvcOptions.add(dataStorageSvcOption);
    }


    /**
     * Convenience method to get specific storage service capability instance
     *
     * @param commonStorageCapabilities common storage capabilities
     * @param capabilityUid  Uid of storage service capability
     * @return  capability instance for specified capability Uid
     */
    public static CapabilityInstance getDataStorageServiceCapability(CommonStorageCapabilities commonStorageCapabilities, CapabilityDefinition.CapabilityUid capabilityUid) {

        CapabilityInstance dataStorageCapabilityInstance = null;
        if (commonStorageCapabilities == null) {
            return null;
        }

        List<DataStorageServiceOption> dataService = commonStorageCapabilities.getDataStorage();
        if (dataService == null) {
            return null;
        }

        for (DataStorageServiceOption dataServiceOption : dataService) {
            List<CapabilityInstance> capabilityList = dataServiceOption.getCapabilities();
            if (capabilityList != null) {
                for (CapabilityInstance ci : capabilityList) {
                    if (ci.getCapabilityDefinitionUid() != null &&
                            ci.getCapabilityDefinitionUid().equals(capabilityUid.toString()) &&
                            ci.getProperties() != null) {
                        dataStorageCapabilityInstance = ci;
                    }
                }
            }
        }
        return dataStorageCapabilityInstance;
    }
}
