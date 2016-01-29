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
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to update the internal flags of Export Group
 * and Initiator objects for RecoverPoint.
 */
public class ProtectionSystemAssocStorageSystemMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(ProtectionSystemAssocStorageSystemMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        updateAssociatedStorageSystemsForPS();
    }

    /**
     * Update ProtectionSystems that need to have the associated storage system blanked out.
     * It will be re-populated during discovery.
     */
    private void updateAssociatedStorageSystemsForPS() {
        DbClient dbClient = getDbClient();
        List<URI> protectionSystemURIs = dbClient.queryByType(ProtectionSystem.class, false);
        Iterator<ProtectionSystem> protectionSystems = dbClient.queryIterativeObjects(ProtectionSystem.class, protectionSystemURIs);
        while (protectionSystems.hasNext()) {
            ProtectionSystem protectionSystem = protectionSystems.next();
            log.info("ProtectionSystem (id={}) must be upgraded", protectionSystem.getId().toString());
            clearAssociatedStorageSystems(protectionSystem, dbClient);
            log.info("ProtectionSystem (id={}) upgraded.", protectionSystem.getId().toString());
        }
    }

    private void clearAssociatedStorageSystems(ProtectionSystem protectionSystem, DbClient dbClient) {
        StringSet associatedStorageSystems = protectionSystem.getAssociatedStorageSystems();
        associatedStorageSystems.clear();
        protectionSystem.setAssociatedStorageSystems(associatedStorageSystems);

        dbClient.persistObject(protectionSystem);
    }
}
