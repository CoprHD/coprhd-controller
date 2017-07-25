/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.migration;

import static java.lang.String.format;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.migrationcontroller.MigrationController;
import com.emc.storageos.model.block.MigrationCreateParam;
import com.emc.storageos.model.block.MigrationEnvironmentParam;
import com.emc.storageos.volumecontroller.ControllerException;

/**
 * Block Migration service implementation.
 */
public class MigrationServiceApiImpl extends AbstractMigrationServiceApiImpl {
    private static final Logger logger = LoggerFactory.getLogger(MigrationServiceApiImpl.class);

    public MigrationServiceApiImpl() {
        super();
    }

    @Override
    public void migrationCreateEnvironment(MigrationEnvironmentParam param, String taskId) {
        try {
            StorageSystem srcSysytem = dbClient.queryObject(StorageSystem.class, param.getSourceStorageSystem());
            URI tgtSysytemId = param.getTargetStorageSystem();

            // TODO define new tag?
            MigrationController controller = getController(MigrationController.class, srcSysytem.getSystemType());
            controller.migrationCreateEnvironment(srcSysytem.getId(), tgtSysytemId, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to create migration environment between source and target systems. "
                    + "Source system: %s, target system: %s", param.getSourceStorageSystem(), param.getTargetStorageSystem());
            logger.error(errorMsg, e);
        }
    }

    @Override
    public void migrationCreate(URI cgId, MigrationCreateParam param, String taskId) {
        try {
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgId);
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, cg.getStorageController());

            MigrationController controller = getController(MigrationController.class, sourceSystem.getSystemType());
            controller.migrationCreate(sourceSystem.getId(), cgId, param.getTargetStorageSystem(), taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to create migration for consistency group %s", cgId);
            logger.error(errorMsg, e);
            dbClient.error(BlockConsistencyGroup.class, cgId, taskId, e);
        }
    }

    @Override
    public void migrationCutover(URI cgId, String taskId) {
        try {
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgId);
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, cg.getStorageController());

            MigrationController controller = getController(MigrationController.class, sourceSystem.getSystemType());
            controller.migrationCutover(sourceSystem.getId(), cgId, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to cutover migration for consistency group %s", cgId);
            logger.error(errorMsg, e);
            dbClient.error(BlockConsistencyGroup.class, cgId, taskId, e);
        }
    }

    @Override
    public void migrationCommit(URI cgId, String taskId) {
        try {
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgId);
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, cg.getStorageController());

            MigrationController controller = getController(MigrationController.class, sourceSystem.getSystemType());
            controller.migrationCommit(sourceSystem.getId(), cgId, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to commit migration for consistency group %s", cgId);
            logger.error(errorMsg, e);
            dbClient.error(BlockConsistencyGroup.class, cgId, taskId, e);
        }
    }

    @Override
    public void migrationCancel(URI cgId, String taskId) {
        try {
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgId);
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, cg.getStorageController());

            MigrationController controller = getController(MigrationController.class, sourceSystem.getSystemType());
            controller.migrationCancel(sourceSystem.getId(), cgId, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to cancel migration for consistency group %s", cgId);
            logger.error(errorMsg, e);
            dbClient.error(BlockConsistencyGroup.class, cgId, taskId, e);
        }
    }

    @Override
    public void migrationRefresh(URI cgId, String taskId) {
        try {
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgId);
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, cg.getStorageController());

            MigrationController controller = getController(MigrationController.class, sourceSystem.getSystemType());
            controller.migrationRefresh(sourceSystem.getId(), cgId, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to refresh migration for consistency group %s", cgId);
            logger.error(errorMsg, e);
            dbClient.error(BlockConsistencyGroup.class, cgId, taskId, e);
        }
    }

    @Override
    public void migrationRecover(URI cgId, String taskId) {
        try {
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgId);
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, cg.getStorageController());

            MigrationController controller = getController(MigrationController.class, sourceSystem.getSystemType());
            controller.migrationRecover(sourceSystem.getId(), cgId, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to recover migration for consistency group %s", cgId);
            logger.error(errorMsg, e);
            dbClient.error(BlockConsistencyGroup.class, cgId, taskId, e);
        }
    }

    @Override
    public void migrationSyncStop(URI cgId, String taskId) {
        try {
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgId);
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, cg.getStorageController());

            MigrationController controller = getController(MigrationController.class, sourceSystem.getSystemType());
            controller.migrationSyncStop(sourceSystem.getId(), cgId, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to sync-stop migration for consistency group %s", cgId);
            logger.error(errorMsg, e);
            dbClient.error(BlockConsistencyGroup.class, cgId, taskId, e);
        }
    }

    @Override
    public void migrationSyncStart(URI cgId, String taskId) {
        try {
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgId);
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, cg.getStorageController());

            MigrationController controller = getController(MigrationController.class, sourceSystem.getSystemType());
            controller.migrationSyncStart(sourceSystem.getId(), cgId, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to sync-start migration for consistency group %s", cgId);
            logger.error(errorMsg, e);
            dbClient.error(BlockConsistencyGroup.class, cgId, taskId, e);
        }
    }

    @Override
    public void migrationRemoveEnvironment(MigrationEnvironmentParam param, String taskId) {
        try {
            StorageSystem srcSysytem = dbClient.queryObject(StorageSystem.class, param.getSourceStorageSystem());
            URI tgtSysytemId = param.getTargetStorageSystem();

            MigrationController controller = getController(MigrationController.class, srcSysytem.getSystemType());
            controller.migrationRemoveEnvironment(srcSysytem.getId(), tgtSysytemId, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to remove migration environment between source and target systems. "
                    + "Source system: %s, target system: %s", param.getSourceStorageSystem(), param.getTargetStorageSystem());
            logger.error(errorMsg, e);
        }
    }

}
