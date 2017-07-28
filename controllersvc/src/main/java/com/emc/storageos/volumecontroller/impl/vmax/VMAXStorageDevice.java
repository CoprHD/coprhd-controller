/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vmax;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.DefaultBlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class VMAXStorageDevice extends DefaultBlockStorageDevice {
    private static final Logger logger = LoggerFactory.getLogger(VMAXStorageDevice.class);

    private DbClient dbClient;
    private VMAXMigrationOperations migrationOperations;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setMigrationOperations(VMAXMigrationOperations migrationOperations) {
        this.migrationOperations = migrationOperations;
    }

    public void doCreateMigrationEnvironment(StorageSystem sourceSystem, StorageSystem targetSystem, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        logger.info(VMAXConstants.CREATE_MIGRATION_ENV + " started");
        migrationOperations.createMigrationEnvironment(sourceSystem, targetSystem, taskCompleter);
        logger.info(VMAXConstants.CREATE_MIGRATION_ENV + " finished");
    }

    public void doRemoveMigrationEnvironment(StorageSystem sourceSystem, StorageSystem targetSystem, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        logger.info(VMAXConstants.REMOVE_MIGRATION_ENV + " started");
        migrationOperations.removeMigrationEnvironment(sourceSystem, targetSystem, taskCompleter);
        logger.info(VMAXConstants.REMOVE_MIGRATION_ENV + " finished");
    }

    public void doCreateMigration(StorageSystem sourceSystem, URI cgURI, URI targetSystemURI, TaskCompleter taskCompleter)
            throws DeviceControllerException {

    }

    public void doCutoverMigration(StorageSystem sourceSystem, URI cgURI, TaskCompleter taskCompleter)
            throws DeviceControllerException {

    }

    public void doCommitMigration(StorageSystem sourceSystem, URI cgURI, TaskCompleter taskCompleter)
            throws DeviceControllerException {

    }

    public void doCancelMigration(StorageSystem sourceSystem, URI cgURI, TaskCompleter taskCompleter)
            throws DeviceControllerException {

    }

    public void doRefreshMigration(StorageSystem sourceSystem, URI cgURI, TaskCompleter taskCompleter)
            throws DeviceControllerException {

    }

    public void doRecoverMigration(StorageSystem sourceSystem, URI cgURI, TaskCompleter taskCompleter)
            throws DeviceControllerException {

    }

    public void doSyncStopMigration(StorageSystem sourceSystem, URI cgURI, TaskCompleter taskCompleter)
            throws DeviceControllerException {

    }

    public void doSyncStartMigration(StorageSystem sourceSystem, URI cgURI, TaskCompleter taskCompleter)
            throws DeviceControllerException {

    }
}
