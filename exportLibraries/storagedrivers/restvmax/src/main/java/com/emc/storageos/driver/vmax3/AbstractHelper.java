/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3;

import com.emc.storageos.driver.vmax3.smc.ManagerFactory;
import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;

/**
 * @author fengs5
 *
 */
public abstract class AbstractHelper {
    ManagerFactory managerFactory;
    AuthenticationInfo authenticationInfo;
    Registry driverRegistry;
    LockManager lockManager;
    RegistryHandler registryHandler;
    String arrayId;

    /**
     * 
     * @param driverRegistry
     * @param arrayId
     */
    public AbstractHelper(Registry driverRegistry, LockManager lockManager, String arrayId) {
        super();
        this.driverRegistry = driverRegistry;
        this.lockManager = lockManager;
        this.arrayId = arrayId;
        registryHandler = new RegistryHandler(driverRegistry);
        authenticationInfo = registryHandler.getAccessInfo(arrayId);
        managerFactory = new ManagerFactory(authenticationInfo);

    }

    /**
     * @param Registry
     */
    public AbstractHelper(Registry driverRegistry, LockManager lockManager) {
        super();
        this.driverRegistry = driverRegistry;
        this.lockManager = lockManager;
        registryHandler = new RegistryHandler(driverRegistry);
    }

}
