/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.db.client.upgrade.MigrateIndexHelper;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * @author cgarber
 * 
 */
public class NetworkVarrayIndexMigration extends BaseCustomMigrationCallback {

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback#process()
     */
    @Override
    public void process() throws MigrationCallbackException {
        InternalDbClient internalDbClient;
        if (InternalDbClient.class.isAssignableFrom(dbClient.getClass())) {
            internalDbClient = (InternalDbClient) dbClient;
        } else {
            throw new IllegalStateException("Migration callback " + name + " needs InternalDbClient");
        }
        MigrateIndexHelper.migrateRemovedIndex(internalDbClient, Network.class, "assignedVirtualArrays", "RelationIndex", "RelationIndex");
        MigrateIndexHelper.migrateRemovedIndex(internalDbClient, Network.class, "connectedVirtualArrays", "RelationIndex", "RelationIndex");
        MigrateIndexHelper.migrateAddedIndex(internalDbClient, Network.class, "assignedVirtualArrays", "RelationIndex");
        MigrateIndexHelper.migrateAddedIndex(internalDbClient, Network.class, "connectedVirtualArrays", "RelationIndex");
    }

}
