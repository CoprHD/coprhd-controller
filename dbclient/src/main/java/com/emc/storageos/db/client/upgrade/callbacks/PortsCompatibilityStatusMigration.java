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
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class PortsCompatibilityStatusMigration extends BaseCustomMigrationCallback {

    private static final Logger log = LoggerFactory.getLogger(PoolsCompatibilityStatusMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        DbClient dbClient = getDbClient();
        List<URI> storageSystemURIs = dbClient.queryByType(StorageSystem.class, true);
        Iterator<StorageSystem> storageSystemObjs = dbClient.queryIterativeObjects(StorageSystem.class, storageSystemURIs);
        while (storageSystemObjs.hasNext()) {
            StorageSystem storageSystem = storageSystemObjs.next();
            URIQueryResultList storagePortURIs = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(storageSystem.getId()),
                    storagePortURIs);
            Iterator<StoragePort> storagePortObjs = dbClient.queryIterativeObjects(StoragePort.class, storagePortURIs);
            List<StoragePort> ports = new ArrayList<StoragePort>();
            while (storagePortObjs.hasNext()) {
                StoragePort port = storagePortObjs.next();
                if (port.getInactive()) {
                    continue;
                }
                log.info("Setting compatibility status of " + port.getId() + " to " + storageSystem.getCompatibilityStatus());
                port.setCompatibilityStatus(storageSystem.getCompatibilityStatus());
                ports.add(port);
            }
            dbClient.persistObject(ports);
        }
    }
}
