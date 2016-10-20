/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DataCollectionJobStatus;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to set the default values for Array Affinity discovery fields in all existing Storage systems.
 * These fields were introduced as part of Host/Array Affinity feature in v3.5
 * 
 */
public class StorageSystemArrayAffinityDiscoveryMigration extends BaseCustomMigrationCallback {
    private static final Logger logger = LoggerFactory.getLogger(StorageSystemArrayAffinityDiscoveryMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        logger.info("Storage system Array affinity discovery migration START");

        DbClient dbClient = getDbClient();
        try {
            List<URI> systemURIs = dbClient.queryByType(StorageSystem.class, true);
            Iterator<StorageSystem> systems = dbClient.queryIterativeObjects(StorageSystem.class, systemURIs, true);
            List<StorageSystem> modifiedSystems = new ArrayList<StorageSystem>();

            while (systems.hasNext()) {
                StorageSystem system = systems.next();
                system.setArrayAffinityStatus(DataCollectionJobStatus.CREATED.name());
                system.setLastArrayAffinityRunTime(0L);
                system.setNextArrayAffinityRunTime(0L);
                system.setSuccessArrayAffinityTime(0L);
                modifiedSystems.add(system);
                logger.info("Updating StorageSystem (id={}) with default values for array affinity discovery statuses",
                        system.getId().toString());
            }
            if (!modifiedSystems.isEmpty()) {
                dbClient.updateObject(modifiedSystems);
            }
        } catch (Exception ex) {
            logger.error("Exception occured while migrating array affinity discovery default values for Storage systems");
            logger.error(ex.getMessage(), ex);
        }
        logger.info("Storage system Array affinity discovery migration END");
    }

}
