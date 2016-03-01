/*
 * Copyright (c) 2014 EMC Corporation
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
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class PoolsCompatibilityStatusMigration extends BaseCustomMigrationCallback {

    private static final Logger log = LoggerFactory.getLogger(PoolsCompatibilityStatusMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        DbClient dbClient = getDbClient();
        List<URI> storageSystemURIs = dbClient.queryByType(StorageSystem.class, true);
        Iterator<StorageSystem> storageSystemObjs = dbClient.queryIterativeObjects(StorageSystem.class, storageSystemURIs);
        while (storageSystemObjs.hasNext()) {
            StorageSystem storageSystem = storageSystemObjs.next();
            URIQueryResultList storagePoolURIs = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceStoragePoolConstraint(storageSystem.getId()),
                    storagePoolURIs);
            Iterator<StoragePool> storagePoolObjs = dbClient.queryIterativeObjects(StoragePool.class, storagePoolURIs);
            List<StoragePool> pools = new ArrayList<StoragePool>();
            while (storagePoolObjs.hasNext()) {
                StoragePool pool = storagePoolObjs.next();
                if (pool.getInactive()) {
                    continue;
                }
                log.info("Setting compatibility status of " + pool.getId() + " to " + storageSystem.getCompatibilityStatus());
                pool.setCompatibilityStatus(storageSystem.getCompatibilityStatus());
                pools.add(pool);
            }
            dbClient.persistObject(pools);
        }
    }
}
