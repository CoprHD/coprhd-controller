/*
 * Copyright (c) 2013 EMC Corporation
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
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to initialize the consistency group for RecoverPoint
 * BlockSnapshots. If the BlockSnapshot is of type RP, we need to copy
 * the parent volume's RP BlockConsistencyGroup to the BlockSnapshot.
 * 
 */
public class RpBlockSnapshotConsistencyGroupMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(RpBlockSnapshotConsistencyGroupMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        updateRecoverPointBlockSnapshots();
    }

    /**
     * Update the BlockSnapshot object to reference the parent Volume
     * BlockConsistencyGroup.
     */
    private void updateRecoverPointBlockSnapshots() {
        log.info("Updating RecoverPoint BlockSnapshots to reference parent Volume's BlockConsistencyGroup.");
        DbClient dbClient = getDbClient();
        List<URI> blockSnapshotURIs = dbClient.queryByType(BlockSnapshot.class, false);
        Iterator<BlockSnapshot> blockSnapshots = dbClient.queryIterativeObjects(BlockSnapshot.class, blockSnapshotURIs);

        while (blockSnapshots.hasNext()) {
            BlockSnapshot blockSnapshot = blockSnapshots.next();
            // Only consider the RP BlockSnapshots
            if (blockSnapshot.getEmName() != null) {
                NamedURI parentVolUri = blockSnapshot.getParent();
                Volume parentVolume = dbClient.queryObject(Volume.class, parentVolUri.getURI());
                if (parentVolume.fetchConsistencyGroupUriByType(dbClient, Types.RP) != null) {
                    URI rpCgUri = parentVolume.fetchConsistencyGroupUriByType(dbClient, Types.RP);
                    blockSnapshot.addConsistencyGroup(rpCgUri.toString());
                    dbClient.persistObject(blockSnapshot);
                    log.info("Updated BlockSnapshot (id={}) to reference parent Volume's BlockConsistencyGroup (id={})",
                            blockSnapshot.getId().toString(), rpCgUri.toString());
                }
            }
        }
    }
}
