/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drivers should extend this class and implement methods in DiscoveryDriver and BlockStorageDriver or FileStorageDevice interfaces.
 */
public abstract class AbstractStorageDriver implements DiscoveryDriver {

    private static final Logger _log = LoggerFactory.getLogger(AbstractStorageDriver.class);
    protected Registry driverRegistry;
    protected LockManager lockManager;

    public AbstractStorageDriver() {
    }

    public void setDriverRegistry(Registry driverRegistry) {
        this.driverRegistry = driverRegistry;
    }

    public void setLockManager(LockManager lockManager) {
        this.lockManager = lockManager;
    }
}
