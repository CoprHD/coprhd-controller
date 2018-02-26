/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.net.URI;

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
    public void createMigrationEnvironment(StorageSystem sourceSystem, StorageSystem targetSystem, TaskCompleter taskCompleter)
            throws DeviceControllerException;

    /**
     * Remove migration environment
     *
     * @param sourceSystem source storage system
     * @param targetSystem target storage system
     * @param taskCompleter the task completer
     *
     * @throws DeviceControllerException
     */
    public void removeMigrationEnvironment(StorageSystem sourceSystem, StorageSystem targetSystem, TaskCompleter taskCompleter)
            throws DeviceControllerException;

    /**
     * Create/Initiate the migration process.
     *
     * @param sourceSystem the source system
     * @param cgURI the cg uri
     * @param migrationURI the migration uri
     * @param targetSystemURI the target system uri
     * @param srp the srp
     * @param enableCompression enable compression
     * @param taskCompleter the task completer
     * @throws ControllerException the controller exception
     */
    public void createMigration(StorageSystem sourceSystem, URI cgURI, URI migrationURI, URI targetSystemURI,
            URI srp, Boolean enableCompression, Boolean validate, TaskCompleter taskCompleter) throws ControllerException;

    /**
     * Cutover the migration process.
     *
     * @param sourceSystem the source system
     * @param cgURI the cg uri
     * @param migrationURI the migration uri
     * @param taskCompleter the task completer
     * @throws ControllerException the controller exception
     */
    public void cutoverMigration(StorageSystem sourceSystem, URI cgURI, URI migrationURI, TaskCompleter taskCompleter)
            throws ControllerException;

    /**
     * Commit the migration process.
     *
     * @param sourceSystem the source system
     * @param cgURI the cg uri
     * @param migrationURI the migration uri
     * @param taskCompleter the task completer
     * @throws ControllerException the controller exception
     */
    public void commitMigration(StorageSystem sourceSystem, URI cgURI, URI migrationURI, TaskCompleter taskCompleter)
            throws ControllerException;

    /**
     * Cancel the migration process.
     *
     * @param sourceSystem the source system
     * @param cgURI the cg uri
     * @param migrationURI the migration uri
     * @param cancelWithRevert
     * @param taskCompleter the task completer
     * @throws ControllerException the controller exception
     */
    public void cancelMigration(StorageSystem sourceSystem, URI cgURI, URI migrationURI, boolean cancelWithRevert,
            TaskCompleter taskCompleter)
            throws ControllerException;

    /**
     * Update the status of the migration job.
     *
     * @param sourceSystem the source system
     * @param cgURI the cg uri
     * @param migrationURI the migration uri
     * @param taskCompleter the task completer
     * @throws ControllerException the controller exception
     */
    public void refreshMigration(StorageSystem sourceSystem, URI cgURI, URI migrationURI, TaskCompleter taskCompleter)
            throws ControllerException;

    /**
     * Recover the migration process.
     *
     * @param sourceSystem the source system
     * @param cgURI the cg uri
     * @param migrationURI the migration uri
     * @param force TODO
     * @param taskCompleter the task completer
     * @throws ControllerException the controller exception
     */
    public void recoverMigration(StorageSystem sourceSystem, URI cgURI, URI migrationURI, boolean force, TaskCompleter taskCompleter)
            throws ControllerException;

    /**
     * Stop the data synchronization of source volumes from target volumes.
     *
     * @param sourceSystem the source system
     * @param cgURI the cg uri
     * @param migrationURI the migration uri
     * @param taskCompleter the task completer
     * @throws ControllerException the controller exception
     */
    public void syncStopMigration(StorageSystem sourceSystem, URI cgURI, URI migrationURI, TaskCompleter taskCompleter)
            throws ControllerException;

    /**
     * Start the data synchronization of source volumes from target volumes.
     *
     * @param sourceSystem the source system
     * @param cgURI the cg uri
     * @param migrationURI the migration uri
     * @param taskCompleter the task completer
     * @throws ControllerException the controller exception
     */
    public void syncStartMigration(StorageSystem sourceSystem, URI cgURI, URI migrationURI, TaskCompleter taskCompleter)
            throws ControllerException;

}
