/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;

/**
 * Interfaces for migration operations
 */
public interface MigrationOperations {
    /**
     * Create migration environment
     *
     * @param sourceSystem source storage system
     * @param targetSystem target storage system
     * @param taskCompleter the task completer
     *
     * @throws DeviceControllerException
     */
    public void createMigrationEnvironment(StorageSystem sourceSystem, StorageSystem targetSystem, TaskCompleter taskCompleter) throws DeviceControllerException;

    /**
     * Remove migration environment
     *
     * @param sourceSystem source storage system
     * @param targetSystem target storage system
     * @param taskCompleter the task completer
     *
     * @throws DeviceControllerException
     */
    public void removeMigrationEnvironment(StorageSystem sourceSystem, StorageSystem targetSystem, TaskCompleter taskCompleter) throws DeviceControllerException;
}
