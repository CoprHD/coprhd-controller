/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.model.block.MigrationCreateParam;
import com.emc.storageos.model.block.MigrationEnvironmentParam;
import com.emc.storageos.volumecontroller.BlockController;

/**
 * Block Migration service implementation.
 */
public class MigrationServiceApiImpl extends AbstractMigrationServiceApiImpl {
    private static final Logger logger = LoggerFactory.getLogger(MigrationServiceApiImpl.class);

    public MigrationServiceApiImpl() {
        super();
    }

    @Override
    public void migrationCreateEnvironment(MigrationEnvironmentParam param, String task) {
        try {
            StorageSystem srcSysytem = dbClient.queryObject(StorageSystem.class, param.getSourceStorageSystem());
            StorageSystem tgtSysytem = dbClient.queryObject(StorageSystem.class, param.getSourceStorageSystem());

            BlockController controller = getController(BlockController.class, srcSysytem.getSystemType());
            // controller.migrationCreateEnvironment(srcSysytem, tgtSysytem);
        } catch (Exception e) {
            logger.error("Failed", e);
        }
    }

    @Override
    public void migrationCreate(URI cgId, MigrationCreateParam param, String task) {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrationCutover(URI cgId, String task) {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrationCommit(URI cgId, String task) {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrationCancel(URI cgId, String task) {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrationRefresh(URI cgId, String task) {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrationRecover(URI cgId, String task) {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrationSyncStop(URI cgId, String task) {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrationSyncStart(URI cgId, String task) {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrationRemoveEnvironment(MigrationEnvironmentParam param, String task) {
        // TODO Auto-generated method stub

    }

}
