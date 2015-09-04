/*
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

import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.db.client.upgrade.MigrateIndexHelper;

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
    public void process() {
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
