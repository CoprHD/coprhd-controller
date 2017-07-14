/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.manager;

import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.vmax3.smc.provider.version.VersionManager;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.StorageGroupManager;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume.VolumeManager;

final public class ManagerFactory {
    private AuthenticationInfo authenticationInfo;

    public ManagerFactory(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;

    }

    public ManagerFactory(String host, Integer port, String userName, String password) {
        this(new AuthenticationInfo(host, port, userName, password));
    }

    public VolumeManager genVolumeManager() {
        return new VolumeManager(authenticationInfo);
    }

    public StorageGroupManager genStorageGroupManager() {
        return new StorageGroupManager(authenticationInfo);
    }

    public VersionManager genVersionManager() {
        return new VersionManager(authenticationInfo);
    }
}
