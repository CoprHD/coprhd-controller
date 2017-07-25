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
import com.emc.storageos.vmax.restapi.VMAXApiClientFactory;
import com.emc.storageos.volumecontroller.DefaultBlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class VMAXRestStorageDevice extends DefaultBlockStorageDevice {
    private static final Logger logger = LoggerFactory.getLogger(VMAXRestStorageDevice.class);

    private DbClient dbClient;
    private VMAXApiClientFactory vmaxApiClientFactory;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setVMAXApiClientFactory(VMAXApiClientFactory vmaxApiClientFactory) {
        this.vmaxApiClientFactory = vmaxApiClientFactory;
    }

    public void doCreateMigrationEnvironment(StorageSystem sourceSystem, URI targetSystemURI, TaskCompleter taskCompleter)
            throws DeviceControllerException {

    }

    public void doRemoveMigrationEnvironment(StorageSystem sourceSystem, URI targetSystemURI, TaskCompleter taskCompleter)
            throws DeviceControllerException {

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
