/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.sdkapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;

public class LocalReplicationManager extends AbstractManager {
    private static final Logger log = LoggerFactory.getLogger(LocalReplicationManager.class);

    /**
     * @param driverRegistry
     * @param lockManager
     */
    public LocalReplicationManager(Registry driverRegistry, LockManager lockManager) {
        super(driverRegistry, lockManager);
    }

    /*
     * /////////////////////////////////////////////////////////////////////////////////////////
     * Functions that with business logic.
     * 
     * /////////////////////////////////////////////////////////////////////////////////////////
     */

    /*
     * /////////////////////////////////////////////////////////////////////////////////////////
     * Functions that communicate with array through rest call.
     * 
     * /////////////////////////////////////////////////////////////////////////////////////////
     */

}
