/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class XtremioBlockSnapshotReplicationGroupInstanceMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(XtremioBlockSnapshotReplicationGroupInstanceMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        updateXioSnapshotReplicationGroupInstance();
    }

    private void updateXioSnapshotReplicationGroupInstance() {
        log.info("Migrating XIO snapshot replication group instance");
        DbClient dbClient = getDbClient();
        List<URI> snapshotURIs = dbClient.queryByType(BlockSnapshot.class, true);
        
        Iterator<BlockSnapshot> snapshots= dbClient.queryIterativeObjects(BlockSnapshot.class, snapshotURIs);
        while( snapshots.hasNext()){
            BlockSnapshot snapshot = snapshots.next();
            URI storageUri = snapshot.getStorageController();
            if (!NullColumnValueGetter.isNullURI(storageUri)) {
                StorageSystem system = dbClient.queryObject(StorageSystem.class, storageUri);
                if(system == null){
                    continue;
                }
                if (DiscoveredDataObject.Type.xtremio.name().equals(system.getSystemType())) {
                    String groupInstance = snapshot.getSnapsetLabel();
                    if (NullColumnValueGetter.isNotNullValue(groupInstance)) {
                        log.info("Setting replicationGroupInstance {} from snapsetLabel", groupInstance);
                        snapshot.setReplicationGroupInstance(groupInstance);
                        dbClient.updateObject(snapshot);
                    }
                }
            }
        }
    }

}
