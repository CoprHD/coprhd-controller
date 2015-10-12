package com.emc.storageos.storagedriver;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drivers should extend this class and implement methods in DiscoveryDriver and BlockStorageDriver interfaces.
 */
public abstract class AbstractStorageDriver implements DiscoveryDriver, BlockStorageDriver {

    private static final Logger _log = LoggerFactory.getLogger(AbstractStorageDriver.class);
    protected Registry driverRegistry;
    protected LockManager lockManager;

    public AbstractStorageDriver() {
    }

    public AbstractStorageDriver(Registry driverRegistry, LockManager lockManager) {
        this.driverRegistry = driverRegistry;
        this.lockManager = lockManager;
    }

    public void setDriverRegistry(Registry driverRegistry) {
        this.driverRegistry = driverRegistry;
    }

    public void setLockManager(LockManager lockManager) {
        this.lockManager = lockManager;
    }
}
