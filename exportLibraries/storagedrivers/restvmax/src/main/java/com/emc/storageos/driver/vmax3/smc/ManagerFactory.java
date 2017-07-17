/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc;

import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.vmax3.smc.provider.version.VersionManager;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.StorageGroupManager;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume.VolumeManager;

final public class ManagerFactory {
    private AuthenticationInfo authenticationInfo;
    private VolumeManager volumeManager;
    private StorageGroupManager storageGroupManager;
    private VersionManager versionManager;

    public ManagerFactory(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;

    }

    public ManagerFactory(String host, Integer port, String userName, String password) {
        this(new AuthenticationInfo(host, port, userName, password));
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

    public VersionManager genVersionManager() {
        if (versionManager == null) {
            versionManager = new VersionManager(authenticationInfo);
        }
        return versionManager;
    }
}
