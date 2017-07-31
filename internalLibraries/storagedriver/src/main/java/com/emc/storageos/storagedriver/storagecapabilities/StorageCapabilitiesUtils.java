/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;

import com.emc.storageos.storagedriver.model.StorageVolume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class with convenience methods for storage capabilities
 */
public class StorageCapabilitiesUtils {

    private static Logger _log = LoggerFactory.getLogger(StorageCapabilitiesUtils.class);
    /**
     * Add new dataStorageServiceOption for  the passed common capability instances
     *
     * @param storageCapabilities common storage capabilities container
     * @param capabilities capability instances for a new data storage service option
     */
    public static void addDataStorageServiceOption(CommonStorageCapabilities storageCapabilities, List<CapabilityInstance> capabilities) {

        // Get the data storage service options for the common capabilities.
        // If null, create it and set it.
        List<CapabilityInstance> dataStorageSvcOptions = storageCapabilities.getDataStorage();
        if (dataStorageSvcOptions == null) {
            dataStorageSvcOptions = new ArrayList<>();
            storageCapabilities.setDataStorage(dataStorageSvcOptions);
        }

        dataStorageSvcOptions.addAll(capabilities);
    }


    /**
     * Convenience method to get specific storage service capability instances
     *
     * @param commonStorageCapabilities common storage capabilities
     * @param capabilityUid  Uid of storage service capability
     * @return  capability instances for specified capability Uid
     */
    public static List<CapabilityInstance> getDataStorageServiceCapability(CommonStorageCapabilities commonStorageCapabilities, CapabilityDefinition.CapabilityUid capabilityUid) {

        if (commonStorageCapabilities == null) {
            return null;
        }

        List<CapabilityInstance> dataService = commonStorageCapabilities.getDataStorage();
        if (dataService == null) {
            return null;
        }

        List<CapabilityInstance> dataStorageCapabilityInstances = new ArrayList<>();
        for (CapabilityInstance ci : dataService) {
            if (ci.getCapabilityDefinitionUid() != null &&
                    ci.getCapabilityDefinitionUid().equals(capabilityUid.toString()) &&
                    ci.getProperties() != null) {
                dataStorageCapabilityInstances.add(ci);
            }
        }
        return dataStorageCapabilityInstances;
    }

    /**
     * Get compression ratio
     * @param driverVolume
     * @return compression ratio
     */
    public static String getVolumeCompressionRatio(StorageVolume driverVolume) {
        // process volume compression from driver volume common capabilities
        String compressionRatio = null;
        List<CapabilityInstance> volumeCompression =
                StorageCapabilitiesUtils.getDataStorageServiceCapability(driverVolume.getCommonCapabilities(), CapabilityDefinition.CapabilityUid.volumeCompression);
        if (volumeCompression != null && !volumeCompression.isEmpty()) {
            _log.info("Compression capability for volume {}: {} ", driverVolume.getNativeId(), volumeCompression.toString());
            compressionRatio = volumeCompression.get(0).getPropertyValue(VolumeCompressionCapabilityDefinition.PROPERTY_NAME.COMPRESSION_RATIO.toString());
        }
        return compressionRatio;
    }
}
