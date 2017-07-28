/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vmax;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.vmax.restapi.VMAXApiClient;
import com.emc.storageos.volumecontroller.MigrationOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class VMAXMigrationOperations extends VMAXOperations implements MigrationOperations {
    private static final Logger logger = LoggerFactory.getLogger(VMAXMigrationOperations.class);
    private static final String MIGRATION_ENV_EXISTS = "The requested migration environment resource already exists";
    private static final String MIGRATION_ENV_NON_EXIST = "A problem occurred deleting the migration environment resources: The migration session environment is not configured";

    @Override
    public void createMigrationEnvironment(StorageSystem sourceSystem, StorageSystem targetSystem, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        logger.info(VMAXConstants.CREATE_MIGRATION_ENV + " started. Source: {}, target: {}", sourceSystem.getSerialNumber(), targetSystem.getSerialNumber());
        try {
            VMAXApiClient apiClient = VMAXUtils.getApiClient(sourceSystem, targetSystem, dbClient, vmaxClientFactory);
            try {
                apiClient.createMigrationEnvironment(sourceSystem.getSerialNumber(), targetSystem.getSerialNumber());
            } catch (Exception e) {
                // ignore if migration environment already exists
                if (!e.getMessage().contains(MIGRATION_ENV_EXISTS)) {
                    throw e;
                }
            }

            taskCompleter.ready(dbClient);
            logger.info(VMAXConstants.CREATE_MIGRATION_ENV + " finished");
        } catch (Exception e) {
            logger.error(VMAXConstants.CREATE_MIGRATION_ENV + " failed", e);
            ServiceError error = DeviceControllerErrors.vmax.methodFailed(VMAXConstants.CREATE_MIGRATION_ENV, e);
            taskCompleter.error(dbClient, error);
        }
    }

    @Override
    public void removeMigrationEnvironment(StorageSystem sourceSystem, StorageSystem targetSystem, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        logger.info(VMAXConstants.REMOVE_MIGRATION_ENV + " started. Source: {}, target: {}", sourceSystem.getSerialNumber(), targetSystem.getSerialNumber());
        try {
            VMAXApiClient apiClient = VMAXUtils.getApiClient(sourceSystem, targetSystem, dbClient, vmaxClientFactory);
            try {
                apiClient.deleteMigrationEnvironment(sourceSystem.getSerialNumber(), targetSystem.getSerialNumber());
            } catch (Exception e) {
                // ignore if migration environment hasn't been configured yet
                if (!e.getMessage().contains(MIGRATION_ENV_NON_EXIST)) {
                    throw e;
                }
            }

            taskCompleter.ready(dbClient);
            logger.info(VMAXConstants.REMOVE_MIGRATION_ENV + " finished");
        } catch (Exception e) {
            logger.error(VMAXConstants.REMOVE_MIGRATION_ENV + " failed", e);
            ServiceError error = DeviceControllerErrors.vmax.methodFailed(VMAXConstants.REMOVE_MIGRATION_ENV, e);
            taskCompleter.error(dbClient, error);
        }
    }
}
