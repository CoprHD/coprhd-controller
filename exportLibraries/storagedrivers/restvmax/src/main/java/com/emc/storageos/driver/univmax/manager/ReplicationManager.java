/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.manager;

import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;

/**
 * All the code is just fake code.
 *
 */
public class ReplicationManager extends DefaultManager {

    /**
     * @param driverRegistry
     * @param arrayId
     */
    public ReplicationManager(Registry driverRegistry, LockManager lockManager, String arrayId) {
        super(driverRegistry, lockManager, arrayId);
    }

}
