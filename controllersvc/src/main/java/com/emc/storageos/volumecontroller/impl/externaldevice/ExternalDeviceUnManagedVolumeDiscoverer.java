/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.storagedriver.DiscoveryDriver;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.volumecontroller.impl.plugins.ExternalDeviceCommunicationInterface;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ExternalDeviceUnManagedVolumeDiscoverer {
    private Logger log = LoggerFactory.getLogger(ExternalDeviceUnManagedVolumeDiscoverer.class);
    public void discoverUnManagedObjects(DiscoveryDriver driver, com.emc.storageos.db.client.model.StorageSystem storageSystem, DbClient dbClient,
                                         PartitionManager partitionManager) {
        log.info("Started discovery of UnManagedVolumes for system {}", storageSystem.getId());

        MutableInt lastPage = new MutableInt(0);
        MutableInt nextPage = new MutableInt(0);
        // prepare storage system
        StorageSystem driverStorageSystem = ExternalDeviceCommunicationInterface.initStorageSystem(storageSystem);
        do {
            List<StorageVolume> driverVolumes = new ArrayList<>();
            driver.getStorageVolumes(driverStorageSystem, driverVolumes, nextPage);
        } while (!nextPage.equals(lastPage));
    }
}
