/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.mutable.MutableBoolean;

import com.emc.storageos.driver.univmax.sdkapi.ExportManager;
import com.emc.storageos.driver.univmax.sdkapi.VolumeManager;
import com.emc.storageos.driver.univmax.sdkapi.discover.DiscoverStorageProvider;
import com.emc.storageos.driver.univmax.sdkapi.discover.DiscoverStorageSystem;
import com.emc.storageos.storagedriver.DefaultStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

public class UniVmaxStorageDriver extends DefaultStorageDriver {

    private DriverDataUtil driverDataUtil;

    public UniVmaxStorageDriver() {
        driverDataUtil = new DriverDataUtil(this);
    }

    public DriverDataUtil getDriverDataUtil() {
        return driverDataUtil;
    }

    @Override
    public DriverTask discoverStorageProvider(StorageProvider storageProvider, List<StorageSystem> storageSystems) {
        return new DiscoverStorageProvider().discoverStorageProvider(driverDataUtil, storageProvider, storageSystems);
    }

    @Override
    public DriverTask discoverStorageSystem(StorageSystem storageSystem) {
        return new DiscoverStorageSystem().discoverStorageSystem(driverDataUtil, storageSystem);
    }

    @Override
    public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        return new VolumeManager().createVolumes(driverDataUtil, volumes, capabilities);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#exportVolumesToInitiators(java.util.List, java.util.List, java.util.Map,
     * java.util.List, java.util.List, com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities,
     * org.apache.commons.lang.mutable.MutableBoolean, java.util.List)
     */
    @Override
    public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes,
            Map<String, String> volumeToHLUMap, List<StoragePort> recommendedPorts, List<StoragePort> availablePorts,
            StorageCapabilities capabilities, MutableBoolean usedRecommendedPorts, List<StoragePort> selectedPorts) {
        return new ExportManager(this.driverRegistry, this.lockManager).exportVolumesToInitiators(initiators, volumes, volumeToHLUMap,
                recommendedPorts, availablePorts, capabilities, usedRecommendedPorts, selectedPorts);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#unexportVolumesFromInitiators(java.util.List, java.util.List)
     */
    @Override
    public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
        return new ExportManager(this.driverRegistry, this.lockManager).unexportVolumesFromInitiators(initiators, volumes);
    }

}
