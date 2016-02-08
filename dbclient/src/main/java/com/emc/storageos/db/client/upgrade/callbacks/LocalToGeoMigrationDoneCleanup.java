/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import static com.emc.storageos.coordinator.client.model.Constants.DB_CONFIG;
import static com.emc.storageos.coordinator.client.model.Constants.GLOBAL_ID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Cleans up the obsolete LOCAL_TO_GEO_DONE flag from ZooKeeper.
 * That flag was an ad-hoc solution to the 1.1 -> 2.0 upgrade, during which access to geo
 * resources need to be redirected to local db until the local-to-geo migration is done.
 */
public class LocalToGeoMigrationDoneCleanup extends BaseCustomMigrationCallback {
    private static final String LOCAL_TO_GEO_DONE = "localtogeo";
    private static final Logger log = LoggerFactory.getLogger(
            LocalToGeoMigrationDoneCleanup.class);

    @Override
    public void process() throws MigrationCallbackException {
        processZKFlagCleanup();
    }

    private void processZKFlagCleanup() {
        Configuration config = coordinatorClient.queryConfiguration(coordinatorClient.getSiteId(), DB_CONFIG, GLOBAL_ID);
        if (config.getConfig(LOCAL_TO_GEO_DONE) != null) {
            log.info("Flag {} found in ZooKeeper. Removing...", LOCAL_TO_GEO_DONE);
            config.removeConfig(LOCAL_TO_GEO_DONE);
            coordinatorClient.persistServiceConfiguration(coordinatorClient.getSiteId(), config);
            log.info("Flag {} removed from ZooKeeper", LOCAL_TO_GEO_DONE);
        } else {
            log.info("Flag {} not found in ZooKeeper.", LOCAL_TO_GEO_DONE);
        }
    }
}
