/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax;

import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;

/**
 * All the code is just fake code.
 *
 */
public class ExportManager extends DefaultManager {

    /**
     * @param driverRegistry
     * @param arrayId
     */
    public ExportManager(Registry driverRegistry, LockManager lockManager, String arrayId) {
        super(driverRegistry, lockManager, arrayId);
    }

}
