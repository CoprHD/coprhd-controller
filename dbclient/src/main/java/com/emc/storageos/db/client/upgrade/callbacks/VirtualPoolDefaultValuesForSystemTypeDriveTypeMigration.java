/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.SupportedDriveTypes;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class VirtualPoolDefaultValuesForSystemTypeDriveTypeMigration extends
        BaseCustomMigrationCallback {

    private static final Logger logger = LoggerFactory
            .getLogger(VirtualPoolDefaultValuesForSystemTypeDriveTypeMigration.class);

    private static final String SYSTEM_TYPE_KEY = "system_type";

    @Override
    public void process() throws MigrationCallbackException {

        logger.info("Migration started");

        DbClient dbClient = getDbClient();
        boolean changed = false;

        try {
            List<URI> virtualPoolUris = dbClient.queryByType(VirtualPool.class,
                    true);
            Iterator<VirtualPool> virtualPools = dbClient
                    .queryIterativeObjects(VirtualPool.class, virtualPoolUris,
                            true);

            logger.info("Processing virtual pool to set default values for drive type and system type as NONE");

            while (virtualPools.hasNext()) {

                VirtualPool virtualPool = virtualPools.next();
                changed = false;

                if (virtualPool.getDriveType() == null) {
                    logger.info(
                            "Setting drive type to NONE for Virtual Pool {}",
                            virtualPool.getId());
                    virtualPool.setDriveType(SupportedDriveTypes.NONE.name());
                    changed = true;
                }

                StringSetMap arrayInfo = virtualPool.getArrayInfo();

                if (arrayInfo != null) {

                    if (arrayInfo.get(SYSTEM_TYPE_KEY) == null) {
                        logger.info(
                                "Setting array system type to NONE for Virtual Pool {}",
                                virtualPool.getId());
                        arrayInfo.put(SYSTEM_TYPE_KEY,
                                VirtualPool.SystemType.NONE.name());
                        changed = true;
                    }

                } else {
                    logger.info(
                            "No existing array info. Creating new array info and setting system type to NONE for Virtual Pool {}",
                            virtualPool.getId());
                    arrayInfo = new StringSetMap();
                    arrayInfo.put(SYSTEM_TYPE_KEY,
                            VirtualPool.SystemType.NONE.name());
                    virtualPool.setArrayInfo(arrayInfo);
                    changed = true;

                }

                if (changed) {
                    logger.info(
                            "Persisting changes into DB for Virtual Pool {}",
                            virtualPool.getId());
                    dbClient.persistObject(virtualPool);
                }

            }
        } catch (Exception ex) {
            logger.error("Exception occured while setting default values to system type and drive type in virtual pool");
            logger.error(ex.getMessage(), ex);
        }

        logger.info("Migration completed successfully");

    }

}
