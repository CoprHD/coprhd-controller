/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels.migration;

import org.apache.commons.lang.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.uimodels.InitialSetup;

import static com.emc.storageos.db.client.model.uimodels.InitialSetup.*;

import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.DataObjectInternalFlagsInitializer;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

@SuppressWarnings("deprecation")
public class InitialSetupDeprecationCallback extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(DataObjectInternalFlagsInitializer.class);

    /**
     * If the InitialSetup CF singleton exists and has the 'complete' attribute set,
     * set the corresponding configuration fields in ZK, then remove the CF row
     */
    @Override
    public void process() throws MigrationCallbackException {
        DbClient dbClient = this.getDbClient();
        InitialSetup initialSetup = dbClient.queryObject(InitialSetup.class, InitialSetup.SINGLETON_ID);
        if ((initialSetup != null) && (BooleanUtils.isTrue(initialSetup.getComplete()))) {
            log.info("Migrating InitialSetup CF into Coordinator");
            ConfigurationImpl config = new ConfigurationImpl();
            config.setKind(CONFIG_KIND);
            config.setId(CONFIG_ID);
            config.setConfig(COMPLETE, Boolean.TRUE.toString());
            coordinatorClient.persistServiceConfiguration(config);
            dbClient.removeObject(initialSetup);
        }
    }
}
