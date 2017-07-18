/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc;

import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.vmax3.smc.symmetrix.config.ConfigManager;
import com.emc.storageos.driver.vmax3.smc.symmetrix.hw.HardwareManager;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.StorageGroupManager;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume.VolumeManager;

final public class ManagerFactory {
    private AuthenticationInfo authenticationInfo;
    private VolumeManager volumeManager;
    private StorageGroupManager storageGroupManager;

    private ConfigManager configManager;
    private HardwareManager hardwareManager;

    public ManagerFactory(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;

    }

    public VolumeManager genVolumeManager() {
        if (volumeManager == null) {
            volumeManager = new VolumeManager(authenticationInfo);
        }
        return volumeManager;
    }

    public StorageGroupManager genStorageGroupManager() {
        if (storageGroupManager == null) {
            storageGroupManager = new StorageGroupManager(authenticationInfo);
        }
        return storageGroupManager;
    }

    public ConfigManager genConfigManager() {
        if (configManager == null) {
            configManager = new ConfigManager(authenticationInfo);
        }
        return configManager;
    }

    public HardwareManager genHardwareManager() {
        if (hardwareManager == null) {
            hardwareManager = new HardwareManager(authenticationInfo);
        }
        return hardwareManager;
    }
}
