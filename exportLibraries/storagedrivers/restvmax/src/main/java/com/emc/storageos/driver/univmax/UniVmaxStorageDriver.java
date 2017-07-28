/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax;

import com.emc.storageos.driver.univmax.sdkapi.VolumeManager;
import com.emc.storageos.driver.univmax.sdkapi.discover.DiscoverStorageProvider;
import com.emc.storageos.storagedriver.DefaultStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

import java.util.List;

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
    public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        return new VolumeManager().createVolumes(driverDataUtil, volumes, capabilities);
    }
}
