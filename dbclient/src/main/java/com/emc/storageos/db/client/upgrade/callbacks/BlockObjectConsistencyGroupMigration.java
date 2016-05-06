/*
 * Copyright (c) 2013 EMC Corporation
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
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to migrate BlockObject consistencyGroup to the new
 * consistencyGroups list field.
 * 
 */
public class BlockObjectConsistencyGroupMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(BlockObjectConsistencyGroupMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        updateVolumeConsistencyGroup();
        updateBlockMirrorConsistencyGroup();
        updateBlockSnapshotConsistencyGroup();
    }

    /**
     * Update the Volume object to migrate the old consistencyGroup field
     * into the new consistencyGroup list field.
     */
    private void updateVolumeConsistencyGroup() {
        log.info("Migrating Volume consistencyGroup to consistencyGroups.");
        DbClient dbClient = getDbClient();
        List<URI> volumeURIs = dbClient.queryByType(Volume.class, false);
        Iterator<Volume> volumes = dbClient.queryIterativeObjects(Volume.class, volumeURIs);

        List<BlockObject> blockObjects = new ArrayList<BlockObject>();

        while (volumes.hasNext()) {
            blockObjects.add(volumes.next());
        }

        migrate(blockObjects);
    }

    /**
     * Update the BlockMirror object to migrate the old consistencyGroup field
     * into the new consistencyGroup list field.
     */
    private void updateBlockMirrorConsistencyGroup() {
        log.info("Migrating BlockMirror consistencyGroup to consistencyGroups.");
        DbClient dbClient = getDbClient();
        List<URI> blockMirrorURIs = dbClient.queryByType(BlockMirror.class, false);
        Iterator<BlockMirror> blockMirrors = dbClient.queryIterativeObjects(BlockMirror.class, blockMirrorURIs);

        List<BlockObject> blockObjects = new ArrayList<BlockObject>();

        while (blockMirrors.hasNext()) {
            blockObjects.add(blockMirrors.next());
        }

        migrate(blockObjects);
    }

    /**
     * Update the BlockSnapshot object to migrate the old consistencyGroup field
     * into the new consistencyGroup list field.
     */
    private void updateBlockSnapshotConsistencyGroup() {
        log.info("Migrating BlockSnapshot consistencyGroup to consistencyGroups.");
        DbClient dbClient = getDbClient();
        List<URI> blockSnapshotURIs = dbClient.queryByType(BlockSnapshot.class, false);
        Iterator<BlockSnapshot> blockSnapshots = dbClient.queryIterativeObjects(BlockSnapshot.class, blockSnapshotURIs);

        List<BlockObject> blockObjects = new ArrayList<BlockObject>();

        while (blockSnapshots.hasNext()) {
            blockObjects.add(blockSnapshots.next());
        }

        migrate(blockObjects);
    }

    private void migrate(List<BlockObject> blockObjects) {
        for (BlockObject blockObject : blockObjects) {
            if (blockObject.getConsistencyGroup() != null) {
                blockObject.addConsistencyGroup(blockObject.getConsistencyGroup().toString());
                blockObject.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                dbClient.persistObject(blockObject);
                log.info("Migrated BlockConsistencyGroup (id={}) on BlockObject (id={}).",
                        blockObject.getConsistencyGroup().toString(), blockObject.getId().toString());
            }
        }
    }
}
