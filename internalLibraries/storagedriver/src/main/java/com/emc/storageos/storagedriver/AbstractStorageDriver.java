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
    protected String sdkVersionNumber;

    public AbstractStorageDriver() {
    }

    public synchronized void setDriverRegistry(Registry driverRegistry) {
        if (this.driverRegistry == null) {
            this.driverRegistry = driverRegistry;
        }
    }

    public synchronized void setLockManager(LockManager lockManager) {
        if (this.lockManager == null) {
            this.lockManager = lockManager;
        }
    }

    public void setSdkVersionNumber(String sdkVersionNumber) {
        this.sdkVersionNumber = sdkVersionNumber;
    }
}
