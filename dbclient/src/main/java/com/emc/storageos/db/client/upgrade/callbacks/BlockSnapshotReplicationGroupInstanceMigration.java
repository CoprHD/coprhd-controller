/*
 * Copyright (c) 2015 EMC Corporation
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
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class BlockSnapshotReplicationGroupInstanceMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(FullCopyVolumeReplicaStateMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        initializeField();
    }

    /**
     * For all full copy volume, set replicaState as DETACHED
     */
    private void initializeField() {
        log.info("Updating block snapshot replication group instance.");
        DbClient dbClient = this.getDbClient();
        List<URI> snapURIs = dbClient.queryByType(BlockSnapshot.class, false);

        Iterator<BlockSnapshot> snaps =
                dbClient.queryIterativeObjects(BlockSnapshot.class, snapURIs);
        while (snaps.hasNext()) {
            BlockSnapshot snapshot = snaps.next();

            log.info("Examining block snapshot (id={}) for upgrade", snapshot.getId().toString());
            String groupInstance = snapshot.getSnapshotGroupInstance();
            if (NullColumnValueGetter.isNotNullValue(groupInstance)) {
                log.info("Setting replicationGroupInstance", groupInstance);
                snapshot.setReplicationGroupInstance(groupInstance);
                dbClient.persistObject(snapshot);
            }

        }
    }

}
