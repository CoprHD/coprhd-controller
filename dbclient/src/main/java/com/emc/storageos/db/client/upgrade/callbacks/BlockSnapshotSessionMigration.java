/*
 * Copyright (c) 2015 EMC Corporation
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
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

/**
 * Migration callback creates a BlockSnapshotSession instance for each VMAX BlockSnapshot and
 * adds the BlockSnapshot to the linked targets list of the newly created BlockSnapshotSession.
 */
public class BlockSnapshotSessionMigration extends BaseCustomMigrationCallback {

    // A reference to a logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionMigration.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void process() {
        s_logger.info("Executing BlockSnapshotSession migration callback.");
        try {
            DbClient dbClient = getDbClient();
            List<BlockSnapshotSession> snapshotSessions = new ArrayList<BlockSnapshotSession>();
            List<URI> snapshotURIs = dbClient.queryByType(BlockSnapshot.class, true);
            Iterator<BlockSnapshot> snapshotsIter = dbClient.queryIterativeObjects(BlockSnapshot.class, snapshotURIs, true);
            while (snapshotsIter.hasNext()) {
                BlockSnapshot snapshot = snapshotsIter.next();
                if (isSnapshotSessionSupported(snapshot)) {
                    // The the storage system for the snapshot supports snapshot
                    // sessions, then we need to prepare and create a snapshot
                    // session for that snapshot and add the snapshot as a linked
                    // target for the session.
                    BlockSnapshotSession snapshotSession = prepareSnapshotSession(snapshot);
                    snapshotSessions.add(snapshotSession);
                }
            }

            if (!snapshotSessions.isEmpty()) {
                dbClient.createObject(snapshotSessions);
            }
        } catch (Exception e) {
            s_logger.error("Caught exception during BlockSnapshotSession migration", e);
        }
    }

    /**
     * Determines if the storage system for the passed BlockSnapshot instance supports
     * snapshot sessions.
     * 
     * @param snapshot A reference to the snapshot.
     * 
     * @return true if the system for the passed snapshot supports snapshot sessions, false otherwise.
     */
    private boolean isSnapshotSessionSupported(BlockSnapshot snapshot) {
        boolean isSupported = false;
        URI systemURI = snapshot.getStorageController();
        StorageSystem system = dbClient.queryObject(StorageSystem.class, systemURI);
        if ((system != null) && (system.checkIfVmax3())) {
            s_logger.info("BlockSnapshotSession supported for snapshot {}:{}", snapshot.getId(), snapshot.getLabel());
            isSupported = true;
        }

        return isSupported;
    }

    /**
     * Prepare the ViPR BlockSnapshotSession instance for the pass BlockSnapshot instance.
     * 
     * @param snapshot A reference to the snapshot.
     * 
     * @return A reference to the newly created snapshot session.
     */
    private BlockSnapshotSession prepareSnapshotSession(BlockSnapshot snapshot) {
        s_logger.info("Prepare BlockSnapshotSession for snapshot {}", snapshot.getId());
        BlockSnapshotSession snapshotSession = new BlockSnapshotSession();
        URI snapSessionURI = URIUtil.createId(BlockSnapshotSession.class);
        snapshotSession.setId(snapSessionURI);
        snapshotSession.setLabel(snapshot.getLabel());
        snapshotSession.setSessionLabel(snapshot.getSnapsetLabel());
        snapshotSession.setParent(snapshot.getParent());
        snapshotSession.setProject(snapshot.getProject());
        snapshotSession.setSessionInstance(snapshot.getSettingsInstance());
        StringSet linkedTargets = new StringSet();
        linkedTargets.add(snapshot.getId().toString());
        snapshotSession.setLinkedTargets(linkedTargets);
        return snapshotSession;
    }
}
