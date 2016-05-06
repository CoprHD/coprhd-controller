/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to initialize the multi-volume consistency field to true for
 * RecoverPoint VirtualPools.
 * 
 */
public class VirtualPoolMultiVolumeConsistencyMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(VirtualPoolMultiVolumeConsistencyMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        updateRecoverPointVirtualPools();
    }

    /**
     * Update RecoverPoint VirtualPools. Ensure the multi volume consistency field
     * is set to true.
     */
    private void updateRecoverPointVirtualPools() {
        log.info("Updating RecoverPoint VirtualPools to enable multi volume consistency.");
        DbClient dbClient = getDbClient();
        List<URI> virtualPoolURIs = dbClient.queryByType(VirtualPool.class, false);
        Iterator<VirtualPool> virtualPools = dbClient.queryIterativeObjects(VirtualPool.class, virtualPoolURIs);

        while (virtualPools.hasNext()) {
            VirtualPool virtualPool = virtualPools.next();

            if (VirtualPool.vPoolSpecifiesProtection(virtualPool) &&
                    (virtualPool.getMultivolumeConsistency() == null || !virtualPool.getMultivolumeConsistency())) {
                virtualPool.setMultivolumeConsistency(true);
                dbClient.persistObject(virtualPool);
                log.info("Updating VirtualPool (id={}) to enable multi volume consistency.", virtualPool.getId().toString());
            }
        }
    }
}
