/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.db.client.upgrade.MigrateIndexHelper;

/**
 * @author cgarber
 * This migration callback handles a change made to the RelationIdex on the protectionVarraySettings 
 * field in the VirtualPool column family. The index table was changed from RelationIndex
 * to VpoolProtRelationIndex
 *
 */
public class VirtualPoolVarrayIndexFix extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(VirtualPoolVarrayIndexFix.class);

    /* (non-Javadoc)
     * @see com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback#process()
     */
    @Override
    public void process() {
        log.info("Fix virtual arrays index for all vpools");
        InternalDbClient internalDbClient;
        if (InternalDbClient.class.isAssignableFrom(dbClient.getClass())) {
            internalDbClient = (InternalDbClient) dbClient;
        } else {
            throw new IllegalStateException("Migration callback " + name + " needs InternalDbClient");
        }
        // removes the entries from RelationIndex (old) index table
        MigrateIndexHelper.migrateRemovedIndex(internalDbClient, VirtualPool.class, "protectionVarraySettings", "RelationIndex",
                "RelationIndex");
        // adds entries to the new index table (framework knows the new index table)
        MigrateIndexHelper.migrateAddedIndex(internalDbClient, VirtualPool.class, "protectionVarraySettings", "RelationIndex");
    }
}

