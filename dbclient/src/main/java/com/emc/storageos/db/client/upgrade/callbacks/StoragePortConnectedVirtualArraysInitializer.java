/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

/**
 * Migration handler to initialize the new connected virtual arrays field for a
 * storage port. Note that this will also automatically initialize the new 
 * tagged virtual arrays field for the storage port, so that we don't need a
 * separate custom callback class for that field.
 */
public class StoragePortConnectedVirtualArraysInitializer extends BaseCustomMigrationCallback {

    private static final Logger log = LoggerFactory.getLogger(StoragePortConnectedVirtualArraysInitializer.class);
    
    @SuppressWarnings("deprecation")
    @Override
    public void process() {
        DbClient dbClient = getDbClient();
        List<URI> storagePortURIs = dbClient.queryByType(StoragePort.class, false);
        Iterator<StoragePort> storagePortsIter = dbClient.queryIterativeObjects(StoragePort.class, storagePortURIs);
        while (storagePortsIter.hasNext()) {
            StoragePort storagePort = storagePortsIter.next();
            String storagePortId = storagePort.getId().toString();
            log.info("Examining StoragePort (id={}) for upgrade", storagePortId);
            URI networkURI = storagePort.getNetwork();
            if (!NullColumnValueGetter.isNullURI(networkURI)) {
                Network network = dbClient.queryObject(Network.class, networkURI);
                if (network != null) {
                    URI varrayURI = network.getVirtualArray();
                    if (!NullColumnValueGetter.isNullURI(varrayURI)) {
                        String varrayId = varrayURI.toString();
                        storagePort.addConnectedVirtualArray(varrayId);
                        dbClient.updateAndReindexObject(storagePort);
                        log.info("Set connected virtual array (id={}) for StoragePort (id={}) for upgrade",
                            varrayId, storagePortId);
                    }
                }
            }
        }
    }
}
