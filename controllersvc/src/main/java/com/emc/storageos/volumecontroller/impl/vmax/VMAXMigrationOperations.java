/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vmax;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.MigrationStatus;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.vmax.restapi.VMAXApiClient;
import com.emc.storageos.vmax.restapi.model.response.migration.MigrationStorageGroupResponse;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.MigrationOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class VMAXMigrationOperations extends VMAXOperations implements MigrationOperations {
    private static final Logger logger = LoggerFactory.getLogger(VMAXMigrationOperations.class);
    private static final String MIGRATION_ENV_EXISTS = "The requested migration environment resource already exists";
    private static final String MIGRATION_ENV_NON_EXIST = "A problem occurred deleting the migration environment resources: The migration session environment is not configured";
    private static final String COMMIT_STORAGE_GROUP_NOT_FOUND_ON_SOURCE_ARRAY = "Storage Group [%s] on Symmetrix [%s] cannot be found";

    @Override
    public void createMigrationEnvironment(StorageSystem sourceSystem, StorageSystem targetSystem, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        logger.info(VMAXConstants.CREATE_MIGRATION_ENV + " started. Source: {}, Target: {}",
                sourceSystem.getSerialNumber(), targetSystem.getSerialNumber());
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
        logger.info(VMAXConstants.REMOVE_MIGRATION_ENV + " started. Source: {}, Target: {}",
                sourceSystem.getSerialNumber(), targetSystem.getSerialNumber());
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

    @Override
    public void createMigration(StorageSystem sourceSystem, URI cgURI, URI migrationURI, URI targetSystemURI,
            URI srp, Boolean enableCompression, TaskCompleter taskCompleter) throws ControllerException {
        logger.info(VMAXConstants.CREATE_MIGRATION + " started");
        try {
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, targetSystemURI);
            Migration migration = dbClient.queryObject(Migration.class, migrationURI);
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            String sgName = cg.getLabel().split(Constants.SMIS_PLUS_REGEX)[2];
            logger.info("Source: {}, Target: {}, Storage Group: {}",
                    sourceSystem.getSerialNumber(), targetSystem.getSerialNumber(), sgName);

            boolean noCompression = !enableCompression;
            String srpName = null;
            if (!NullColumnValueGetter.isNullURI(srp)) {
                StoragePool srpPool = dbClient.queryObject(StoragePool.class, srp);
                srpName = srpPool.getNativeId();
            }

            VMAXApiClient apiClient = VMAXUtils.getApiClient(sourceSystem, targetSystem, dbClient, vmaxClientFactory);
            // TODO check if SG exists. SG GET API works only after create() is initiated.
            // MigrationStorageGroupResponse sgResponse = apiClient.getMigrationStorageGroup(sourceSystem.getSerialNumber(), sgName);

            // update migration start time
            long currentTime = System.currentTimeMillis();
            migration.setStartTime(String.valueOf(currentTime));
            dbClient.updateObject(migration);

            apiClient.createMigration(sourceSystem.getSerialNumber(), targetSystem.getSerialNumber(), sgName, noCompression, srpName);

            taskCompleter.ready(dbClient);
            logger.info(VMAXConstants.CREATE_MIGRATION + " finished");
        } catch (Exception e) {
            logger.error(VMAXConstants.CREATE_MIGRATION + " failed", e);
            ServiceError error = DeviceControllerErrors.vmax.methodFailed(VMAXConstants.CREATE_MIGRATION, e);
            taskCompleter.error(dbClient, error);
        }
    }

    @Override
    public void cutoverMigration(StorageSystem sourceSystem, URI cgURI, URI migrationURI, TaskCompleter taskCompleter)
            throws ControllerException {
        logger.info(VMAXConstants.CUTOVER_MIGRATION + " started");
        try {
            Migration migration = dbClient.queryObject(Migration.class, migrationURI);
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, migration.getTargetSystem());
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            String sgName = cg.getLabel().split(Constants.SMIS_PLUS_REGEX)[2];
            logger.info("Source: {}, Target: {}, Storage Group: {}",
                    sourceSystem.getSerialNumber(), targetSystem.getSerialNumber(), sgName);

            VMAXApiClient apiClient = VMAXUtils.getApiClient(sourceSystem, targetSystem, dbClient, vmaxClientFactory);
            // TODO validate the SG status for this operation
            MigrationStorageGroupResponse sgResponse = apiClient.getMigrationStorageGroup(sourceSystem.getSerialNumber(), sgName);

            apiClient.cutoverMigration(sourceSystem.getSerialNumber(), sgName);

            taskCompleter.ready(dbClient);
            logger.info(VMAXConstants.CUTOVER_MIGRATION + " finished");
        } catch (Exception e) {
            logger.error(VMAXConstants.CUTOVER_MIGRATION + " failed", e);
            ServiceError error = DeviceControllerErrors.vmax.methodFailed(VMAXConstants.CUTOVER_MIGRATION, e);
            taskCompleter.error(dbClient, error);
        }
    }

    @Override
    public void commitMigration(StorageSystem sourceSystem, URI cgURI, URI migrationURI, TaskCompleter taskCompleter)
            throws ControllerException {
        logger.info(VMAXConstants.COMMIT_MIGRATION + " started");
        String sgName = null;
        try {
            Migration migration = dbClient.queryObject(Migration.class, migrationURI);
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, migration.getTargetSystem());
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            sgName = cg.getLabel().split(Constants.SMIS_PLUS_REGEX)[2];
            logger.info("Source: {}, Target: {}, Storage Group: {}",
                    sourceSystem.getSerialNumber(), targetSystem.getSerialNumber(), sgName);

            VMAXApiClient apiClient = VMAXUtils.getApiClient(sourceSystem, targetSystem, dbClient, vmaxClientFactory);
            // validate the SG status for this operation
            MigrationStorageGroupResponse sgResponse = apiClient.getMigrationStorageGroup(sourceSystem.getSerialNumber(), sgName);

            try {
                apiClient.commitMigration(sourceSystem.getSerialNumber(), sgName);
            } catch (Exception e) {
                // ignore the SG on source array not found error.
                // If the SG had DeleteWhenUnassociated flag set during its creation, we will get SG not found error after commit
                String msg = String.format(COMMIT_STORAGE_GROUP_NOT_FOUND_ON_SOURCE_ARRAY, sgName, sourceSystem.getSerialNumber());
                if (!e.getMessage().contains(msg)) {
                    throw e;
                }
            }

            // update migration end time
            long currentTime = System.currentTimeMillis();
            migration.setEndTime(String.valueOf(currentTime));
            // migration.setStatus("DONE"); // update migration status as Completed
            dbClient.updateObject(migration);

            taskCompleter.ready(dbClient);
            logger.info(VMAXConstants.COMMIT_MIGRATION + " finished");
        } catch (Exception e) {
            logger.error(VMAXConstants.COMMIT_MIGRATION + " failed", e);
            ServiceError error = DeviceControllerErrors.vmax.methodFailed(VMAXConstants.COMMIT_MIGRATION, e);
            taskCompleter.error(dbClient, error);
        }
    }

    @Override
    public void cancelMigration(StorageSystem sourceSystem, URI cgURI, URI migrationURI, TaskCompleter taskCompleter)
            throws ControllerException {
        logger.info(VMAXConstants.CANCEL_MIGRATION + " started");
        try {
            Migration migration = dbClient.queryObject(Migration.class, migrationURI);
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, migration.getTargetSystem());
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            String sgName = cg.getLabel().split(Constants.SMIS_PLUS_REGEX)[2];
            logger.info("Source: {}, Target: {}, Storage Group: {}",
                    sourceSystem.getSerialNumber(), targetSystem.getSerialNumber(), sgName);

            VMAXApiClient apiClient = VMAXUtils.getApiClient(sourceSystem, targetSystem, dbClient, vmaxClientFactory);
            // validate the SG status for this operation
            MigrationStorageGroupResponse sgResponse = apiClient.getMigrationStorageGroup(sourceSystem.getSerialNumber(), sgName);

            if (sgResponse.getState() != null &&
                    (sgResponse.getState().equalsIgnoreCase(MigrationStatus.Migrating.name())
                            || sgResponse.getState().equalsIgnoreCase(MigrationStatus.CutoverSync.name()))) {
                apiClient.cancelMigrationWithRevert(sourceSystem.getSerialNumber(), sgName);
            } else {
                apiClient.cancelMigration(sourceSystem.getSerialNumber(), sgName);
            }

            taskCompleter.ready(dbClient);
            logger.info(VMAXConstants.CANCEL_MIGRATION + " finished");
        } catch (Exception e) {
            logger.error(VMAXConstants.CANCEL_MIGRATION + " failed", e);
            ServiceError error = DeviceControllerErrors.vmax.methodFailed(VMAXConstants.CANCEL_MIGRATION, e);
            taskCompleter.error(dbClient, error);
        }
    }

    @Override
    public void refreshMigration(StorageSystem sourceSystem, URI cgURI, URI migrationURI, TaskCompleter taskCompleter)
            throws ControllerException {
        logger.info(VMAXConstants.REFRESH_MIGRATION + " started");
        try {
            Migration migration = dbClient.queryObject(Migration.class, migrationURI);
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, migration.getTargetSystem());
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            String sgName = cg.getLabel().split(Constants.SMIS_PLUS_REGEX)[2];
            logger.info("Source: {}, Target: {}, Storage Group: {}",
                    sourceSystem.getSerialNumber(), targetSystem.getSerialNumber(), sgName);

            VMAXApiClient apiClient = VMAXUtils.getApiClient(sourceSystem, targetSystem, dbClient, vmaxClientFactory);
            MigrationStorageGroupResponse sgResponse = apiClient.getMigrationStorageGroup(sourceSystem.getSerialNumber(), sgName);
            // update latest migration status in BCG and Migration
            String status = sgResponse.getState();
            cg.setMigrationStatus(status);
            dbClient.updateObject(cg);
            migration.setMigrationStatus(status);
            dbClient.updateObject(migration);

            taskCompleter.ready(dbClient);
            logger.info(VMAXConstants.REFRESH_MIGRATION + " finished");
        } catch (Exception e) {
            logger.error(VMAXConstants.REFRESH_MIGRATION + " failed", e);
            ServiceError error = DeviceControllerErrors.vmax.methodFailed(VMAXConstants.REFRESH_MIGRATION, e);
            taskCompleter.error(dbClient, error);
        }
    }

    @Override
    public void recoverMigration(StorageSystem sourceSystem, URI cgURI, URI migrationURI, boolean force, TaskCompleter taskCompleter)
            throws ControllerException {
        logger.info(VMAXConstants.RECOVER_MIGRATION + " started");
        try {
            Migration migration = dbClient.queryObject(Migration.class, migrationURI);
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, migration.getTargetSystem());
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            String sgName = cg.getLabel().split(Constants.SMIS_PLUS_REGEX)[2];
            logger.info("Source: {}, Target: {}, Storage Group: {}",
                    sourceSystem.getSerialNumber(), targetSystem.getSerialNumber(), sgName);

            VMAXApiClient apiClient = VMAXUtils.getApiClient(sourceSystem, targetSystem, dbClient, vmaxClientFactory);
            // validate the SG status for this operation
            MigrationStorageGroupResponse sgResponse = apiClient.getMigrationStorageGroup(sourceSystem.getSerialNumber(), sgName);

            apiClient.recoverMigration(sourceSystem.getSerialNumber(), sgName, force);

            taskCompleter.ready(dbClient);
            logger.info(VMAXConstants.RECOVER_MIGRATION + " finished");
        } catch (Exception e) {
            logger.error(VMAXConstants.RECOVER_MIGRATION + " failed", e);
            ServiceError error = DeviceControllerErrors.vmax.methodFailed(VMAXConstants.RECOVER_MIGRATION, e);
            taskCompleter.error(dbClient, error);
        }
    }

    @Override
    public void syncStopMigration(StorageSystem sourceSystem, URI cgURI, URI migrationURI, TaskCompleter taskCompleter)
            throws ControllerException {
        logger.info(VMAXConstants.SYNCSTOP_MIGRATION + " started");
        try {
            Migration migration = dbClient.queryObject(Migration.class, migrationURI);
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, migration.getTargetSystem());
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            String sgName = cg.getLabel().split(Constants.SMIS_PLUS_REGEX)[2];
            logger.info("Source: {}, Target: {}, Storage Group: {}",
                    sourceSystem.getSerialNumber(), targetSystem.getSerialNumber(), sgName);

            VMAXApiClient apiClient = VMAXUtils.getApiClient(sourceSystem, targetSystem, dbClient, vmaxClientFactory);
            // validate the SG status for this operation
            MigrationStorageGroupResponse sgResponse = apiClient.getMigrationStorageGroup(sourceSystem.getSerialNumber(), sgName);

            apiClient.stopMigrationSync(sourceSystem.getSerialNumber(), sgName);

            taskCompleter.ready(dbClient);
            logger.info(VMAXConstants.SYNCSTOP_MIGRATION + " finished");
        } catch (Exception e) {
            logger.error(VMAXConstants.SYNCSTOP_MIGRATION + " failed", e);
            ServiceError error = DeviceControllerErrors.vmax.methodFailed(VMAXConstants.SYNCSTOP_MIGRATION, e);
            taskCompleter.error(dbClient, error);
        }
    }

    @Override
    public void syncStartMigration(StorageSystem sourceSystem, URI cgURI, URI migrationURI, TaskCompleter taskCompleter)
            throws ControllerException {
        logger.info(VMAXConstants.SYNCSTART_MIGRATION + " started");
        try {
            Migration migration = dbClient.queryObject(Migration.class, migrationURI);
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, migration.getTargetSystem());
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            String sgName = cg.getLabel().split(Constants.SMIS_PLUS_REGEX)[2];
            logger.info("Source: {}, Target: {}, Storage Group: {}",
                    sourceSystem.getSerialNumber(), targetSystem.getSerialNumber(), sgName);

            VMAXApiClient apiClient = VMAXUtils.getApiClient(sourceSystem, targetSystem, dbClient, vmaxClientFactory);
            // validate the SG status for this operation
            MigrationStorageGroupResponse sgResponse = apiClient.getMigrationStorageGroup(sourceSystem.getSerialNumber(), sgName);

            apiClient.startMigrationSync(sourceSystem.getSerialNumber(), sgName);

            taskCompleter.ready(dbClient);
            logger.info(VMAXConstants.SYNCSTART_MIGRATION + " finished");
        } catch (Exception e) {
            logger.error(VMAXConstants.SYNCSTART_MIGRATION + " failed", e);
            ServiceError error = DeviceControllerErrors.vmax.methodFailed(VMAXConstants.SYNCSTART_MIGRATION, e);
            taskCompleter.error(dbClient, error);
        }
    }
}
