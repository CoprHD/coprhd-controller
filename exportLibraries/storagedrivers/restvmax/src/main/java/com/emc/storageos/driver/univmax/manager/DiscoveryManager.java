/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.manager;

import java.util.List;
import java.util.UUID;

import com.emc.storageos.driver.univmax.smc.EngineFactory;
import com.emc.storageos.driver.univmax.smc.basetype.AuthenticationInfo;
import com.emc.storageos.storagedriver.DefaultDriverTask;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;

/**
 * All the code is just fake code.
 *
 */
public class DiscoveryManager extends DefaultManager {

    /**
     * 
     */
    public DiscoveryManager(Registry driverRegistry, LockManager lockManager, String arrayId) {
        super(driverRegistry, lockManager, arrayId);
    }

    /**
     * This constructor used for discovering provider.
     * 
     * @param driverRegistry
     * @param protocol
     * @param host
     * @param port
     * @param username
     * @param password
     */
    public DiscoveryManager(Registry driverRegistry, LockManager lockManager, String protocol, String host, int port, String username,
            String password) {
        super(driverRegistry, lockManager);
        AuthenticationInfo authenticationInfo = new AuthenticationInfo(protocol, host, port, username, password);
        managerFactory = new EngineFactory(authenticationInfo);
    }

    // TODO: fake implementation
    public DriverTask discoverStorageProvider(StorageProvider storageProvider, List<StorageSystem> storageSystems) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "discover-storage-provider", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        // fetch storageSystem from array
        for (StorageSystem ss : storageSystems) {

            this.registryHandler.setAccessInfo(ss.getSerialNumber(), authenticationInfo.getProtocol(), authenticationInfo.getHost(),
                    authenticationInfo.getPort(), authenticationInfo.getUserName(),
                    authenticationInfo.getPassword());
        }
        return task;
    }

    // TODO: fake implementation
    public DriverTask discoverStorageSystem(StorageSystem storageSystem) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "discover-storage-provider", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        // call configManager to discover ss
        return task;
    }

}
