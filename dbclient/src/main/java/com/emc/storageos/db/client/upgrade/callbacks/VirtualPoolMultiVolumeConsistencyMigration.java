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
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

/**
 * Migration handler to initialize the multi-volume consistency field to true for
 * RecoverPoint VirtualPools.
 * 
 */
public class VirtualPoolMultiVolumeConsistencyMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(VirtualPoolMultiVolumeConsistencyMigration.class);
    
    @Override
    public void process() {
        updateRecoverPointVirtualPools();
    }

    /**
     * Update RecoverPoint VirtualPools.  Ensure the multi volume consistency field
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
