/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.storagedriver.DefaultStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageHostComponent;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

public class Vmax3StorageDriver extends DefaultStorageDriver {
    private static final Logger _log = LoggerFactory.getLogger(Vmax3StorageDriver.class);

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#discoverStorageSystem(com.emc.storageos.storagedriver.model.StorageSystem)
     */
    @Override
    public DriverTask discoverStorageSystem(StorageSystem storageSystem) {
        return new DiscoveryHelper(this.driverRegistry, this.lockManager, storageSystem.getSerialNumber())
                .discoverStorageSystem(storageSystem);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#discoverStoragePools(com.emc.storageos.storagedriver.model.StorageSystem,
     * java.util.List)
     */
    @Override
    public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {
        // TODO Auto-generated method stub
        return super.discoverStoragePools(storageSystem, storagePools);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#discoverStoragePorts(com.emc.storageos.storagedriver.model.StorageSystem,
     * java.util.List)
     */
    @Override
    public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
        // TODO Auto-generated method stub
        return super.discoverStoragePorts(storageSystem, storagePorts);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.storagedriver.DefaultStorageDriver#discoverStorageHostComponents(com.emc.storageos.storagedriver.model.StorageSystem
     * , java.util.List)
     */
    @Override
    public DriverTask discoverStorageHostComponents(StorageSystem storageSystem, List<StorageHostComponent> embeddedStorageHostComponents) {
        // TODO Auto-generated method stub
        return super.discoverStorageHostComponents(storageSystem, embeddedStorageHostComponents);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.storagedriver.DefaultStorageDriver#discoverStorageProvider(com.emc.storageos.storagedriver.model.StorageProvider,
     * java.util.List)
     */
    @Override
    public DriverTask discoverStorageProvider(StorageProvider storageProvider, List<StorageSystem> storageSystems) {
        String protocol = storageProvider.getUseSSL() ? "https" : "http";
        String host = storageProvider.getProviderHost();
        int port = storageProvider.getPortNumber();
        String username = storageProvider.getUsername();
        String passwd = storageProvider.getPassword();
        return new DiscoveryHelper(this.driverRegistry, this.lockManager, protocol, host, port, username, passwd).discoverStorageProvider(
                storageProvider,
                storageSystems);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#createVolumes(java.util.List,
     * com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities)
     */
    @Override
    public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities) {

        return new ProvisioningHelper(this.driverRegistry, this.lockManager, volumes.get(0).getStorageSystemId()).createVolumes(volumes,
                capabilities);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.storagedriver.DefaultStorageDriver#createConsistencyGroup(com.emc.storageos.storagedriver.model.VolumeConsistencyGroup
     * )
     */
    @Override
    public DriverTask createConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
        consistencyGroup.getStorageSystemId();
        // TODO Auto-generated method stub
        return super.createConsistencyGroup(consistencyGroup);
    }

}
