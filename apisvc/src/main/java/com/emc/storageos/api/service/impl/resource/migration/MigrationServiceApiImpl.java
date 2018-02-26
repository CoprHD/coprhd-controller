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

    @Override
    public void migrationCreateEnvironment(MigrationEnvironmentParam param, String taskId) {
        try {
            StorageSystem srcSysytem = dbClient.queryObject(StorageSystem.class, param.getSourceStorageSystem());
            URI tgtSysytemURI = param.getTargetStorageSystem();

            MigrationController controller = getController(MigrationController.class, MigrationController.MIGRATION);
            controller.migrationCreateEnvironment(srcSysytem.getId(), tgtSysytemURI, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to create migration environment between source and target systems. "
                    + "Source system: %s, Target system: %s", param.getSourceStorageSystem(), param.getTargetStorageSystem());
            logger.error(errorMsg, e);
            dbClient.error(StorageSystem.class, param.getSourceStorageSystem(), taskId, e);
        }
    }

    @Override
    public void migrationCreate(URI cgURI, URI migrationURI, MigrationCreateParam param, String taskId) {
        String storageGroupName = null;
        try {
            storageGroupName = cgURI.toString();
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            storageGroupName = cg.getStorageGroupName();
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, cg.getStorageController());

            MigrationController controller = getController(MigrationController.class, MigrationController.MIGRATION);
            controller.migrationCreate(sourceSystem.getId(), cgURI, migrationURI, param.getTargetStorageSystem(), param.getSrp(),
                    param.getCompressionEnabled(), param.getValidate(), taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to create migration for storage group %s", storageGroupName);
            logger.error(errorMsg, e);
            dbClient.error(BlockConsistencyGroup.class, cgURI, taskId, e);
        }
    }

    @Override
    public void migrationCutover(URI cgURI, URI migrationURI, String taskId) {
        String storageGroupName = null;
        try {
            storageGroupName = cgURI.toString();
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            storageGroupName = cg.getStorageGroupName();
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, cg.getStorageController());

            MigrationController controller = getController(MigrationController.class, MigrationController.MIGRATION);
            controller.migrationCutover(sourceSystem.getId(), cgURI, migrationURI, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to cutover migration for storage group %s", storageGroupName);
            logger.error(errorMsg, e);
            dbClient.error(BlockConsistencyGroup.class, cgURI, taskId, e);
        }
    }

    @Override
    public void migrationCommit(URI cgURI, URI migrationURI, String taskId) {
        String storageGroupName = null;
        try {
            storageGroupName = cgURI.toString();
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            storageGroupName = cg.getStorageGroupName();
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, cg.getStorageController());

            MigrationController controller = getController(MigrationController.class, MigrationController.MIGRATION);
            controller.migrationCommit(sourceSystem.getId(), cgURI, migrationURI, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to commit migration for storage group %s", storageGroupName);
            logger.error(errorMsg, e);
            dbClient.error(BlockConsistencyGroup.class, cgURI, taskId, e);
        }
    }

    @Override
    public void migrationCancel(URI cgURI, URI migrationURI, boolean cancelWithRevert, String taskId) {
        String storageGroupName = null;
        try {
            storageGroupName = cgURI.toString();
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            storageGroupName = cg.getStorageGroupName();
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, cg.getStorageController());

            MigrationController controller = getController(MigrationController.class, MigrationController.MIGRATION);
            controller.migrationCancel(sourceSystem.getId(), cgURI, migrationURI, cancelWithRevert, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to cancel migration for storage group %s", storageGroupName);
            logger.error(errorMsg, e);
            dbClient.error(BlockConsistencyGroup.class, cgURI, taskId, e);
        }
    }

    @Override
    public void migrationRefresh(URI cgURI, URI migrationURI, String taskId) {
        String storageGroupName = null;
        try {
            storageGroupName = cgURI.toString();
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            storageGroupName = cg.getStorageGroupName();
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, cg.getStorageController());

            MigrationController controller = getController(MigrationController.class, MigrationController.MIGRATION);
            controller.migrationRefresh(sourceSystem.getId(), cgURI, migrationURI, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to refresh migration for storage group %s", storageGroupName);
            logger.error(errorMsg, e);
            dbClient.error(BlockConsistencyGroup.class, cgURI, taskId, e);
        }
    }

    @Override
    public void migrationRecover(URI cgURI, URI migrationURI, boolean force, String taskId) {
        String storageGroupName = null;
        try {
            storageGroupName = cgURI.toString();
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            storageGroupName = cg.getStorageGroupName();
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, cg.getStorageController());

            MigrationController controller = getController(MigrationController.class, MigrationController.MIGRATION);
            controller.migrationRecover(sourceSystem.getId(), cgURI, migrationURI, force, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to recover migration for storage group %s", storageGroupName);
            logger.error(errorMsg, e);
            dbClient.error(BlockConsistencyGroup.class, cgURI, taskId, e);
        }
    }

    @Override
    public void migrationSyncStop(URI cgURI, URI migrationURI, String taskId) {
        String storageGroupName = null;
        try {
            storageGroupName = cgURI.toString();
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            storageGroupName = cg.getStorageGroupName();
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, cg.getStorageController());

            MigrationController controller = getController(MigrationController.class, MigrationController.MIGRATION);
            controller.migrationSyncStop(sourceSystem.getId(), cgURI, migrationURI, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to sync-stop migration for storage group %s", storageGroupName);
            logger.error(errorMsg, e);
            dbClient.error(BlockConsistencyGroup.class, cgURI, taskId, e);
        }
    }

    @Override
    public void migrationSyncStart(URI cgURI, URI migrationURI, String taskId) {
        String storageGroupName = null;
        try {
            storageGroupName = cgURI.toString();
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            storageGroupName = cg.getStorageGroupName();
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, cg.getStorageController());

            MigrationController controller = getController(MigrationController.class, MigrationController.MIGRATION);
            controller.migrationSyncStart(sourceSystem.getId(), cgURI, migrationURI, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to sync-start migration for storage group %s", storageGroupName);
            logger.error(errorMsg, e);
            dbClient.error(BlockConsistencyGroup.class, cgURI, taskId, e);
        }
    }

    @Override
    public void migrationRemoveEnvironment(MigrationEnvironmentParam param, String taskId) {
        try {
            StorageSystem srcSysytem = dbClient.queryObject(StorageSystem.class, param.getSourceStorageSystem());
            URI tgtSysytemURI = param.getTargetStorageSystem();

            MigrationController controller = getController(MigrationController.class, srcSysytem.getSystemType());
            controller.migrationRemoveEnvironment(srcSysytem.getId(), tgtSysytemURI, taskId);
        } catch (ControllerException e) {
            String errorMsg = format("Failed to remove migration environment between source and target systems. "
                    + "Source system: %s, Target system: %s", param.getSourceStorageSystem(), param.getTargetStorageSystem());
            logger.error(errorMsg, e);
            dbClient.error(StorageSystem.class, param.getSourceStorageSystem(), taskId, e);
        }
    }

}
