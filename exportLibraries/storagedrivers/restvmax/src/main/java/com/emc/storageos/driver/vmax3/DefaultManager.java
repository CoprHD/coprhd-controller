/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3;

import com.emc.storageos.driver.vmax3.smc.EngineFactory;
import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;

/**
 * @author fengs5
 *
 */
public class DefaultManager {
    EngineFactory managerFactory;
    AuthenticationInfo authenticationInfo;
    Registry driverRegistry;
    LockManager lockManager;
    RegistryHandler registryHandler;
    String arrayId;

    /**
     * 
     * @param driverRegistry
     * @param lockManager
     * @param arrayId
     */
    public DefaultManager(Registry driverRegistry, LockManager lockManager, String arrayId) {
        super();
        this.driverRegistry = driverRegistry;
        this.lockManager = lockManager;
        this.arrayId = arrayId;
        registryHandler = new RegistryHandler(driverRegistry);
        authenticationInfo = registryHandler.getAccessInfo(arrayId);
        managerFactory = new EngineFactory(authenticationInfo);

    }

    /**
     * 
     * @param driverRegistry
     * @param lockManager
     */
    public DefaultManager(Registry driverRegistry, LockManager lockManager) {
        super();
        this.driverRegistry = driverRegistry;
        this.lockManager = lockManager;
        registryHandler = new RegistryHandler(driverRegistry);
    }

}
