/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

public class StoragePoolDiscoveryStatusMigration extends
        BaseCustomMigrationCallback {
    
    private static final Logger log = LoggerFactory.getLogger(StoragePoolDiscoveryStatusMigration.class);

    @Override
    public void process() {
        DbClient dbClient = getDbClient();
        List<URI> poolIds = dbClient.queryByType(StoragePool.class, true);
        Iterator<StoragePool> pools = dbClient.queryIterativeObjects(StoragePool.class, poolIds);
        List<StoragePool> modifiedPools = new ArrayList<StoragePool>();
        while (pools.hasNext()) {
            // set default value of DiscoveryStatus to VISIBLE
            StoragePool pool = pools.next();
            log.info("Setting discovery status of " + pool.getId() +
                    " to " + DiscoveryStatus.VISIBLE);
            pool.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
            modifiedPools.add(pool);
        }        
        dbClient.persistObject(modifiedPools);
    }

}
