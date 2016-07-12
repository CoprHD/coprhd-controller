/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.rollback;

import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Implementation of {@link ReplicaCleanup} for deleting failed instances of {@link BlockSnapshot} and
 * updating any existing {@link BlockSnapshotSession} instances.
 */
public class BlockSnapshotCleanup extends ReplicaCleanup {

    private static final Logger log = LoggerFactory.getLogger(BlockSnapshotCleanup.class);

    /**
     * Cleans up instances of {@link BlockSnapshot} that failed to be created. Also, any stale entries in
     * {@link BlockSnapshotSession#getLinkedTargets()} will be cleaned up.
     *
     * @param volume        Volume URI to process.
     * @param itemsToUpdate Items to be updated.
     * @param itemsToDelete Items to be deleted.
     */
    @Override
    public void process(URI volume, Collection<DataObject> itemsToUpdate, Collection<DataObject> itemsToDelete) {
        List<BlockSnapshot> snapshots = CustomQueryUtility.queryActiveResourcesByConstraint(getDbClient(),
                BlockSnapshot.class, ContainmentConstraint.Factory.getVolumeSnapshotConstraint(volume));
        List<BlockSnapshot> failedSnapshots = new ArrayList<>();
        List<BlockSnapshotSession> updateSessions = new ArrayList<>();

        failedSnapshots.addAll(Collections2.filter(snapshots, new Predicate<BlockSnapshot>() {
            @Override
            public boolean apply(BlockSnapshot snapshot) {
                return Strings.isNullOrEmpty(snapshot.getNativeId());
            }
        }));

        // Removed failed snapshots from any existing sessions
        for (BlockSnapshot failedSnapshot : failedSnapshots) {
            log.info("Removing failed snapshot: {}", failedSnapshot.getLabel());
            List<BlockSnapshotSession> sessions = CustomQueryUtility.queryActiveResourcesByConstraint(getDbClient(),
                    BlockSnapshotSession.class,
                    ContainmentConstraint.Factory.getLinkedTargetSnapshotSessionConstraint(failedSnapshot.getId()));

            for (BlockSnapshotSession session : sessions) {
                log.info("Updating existing session: {}", session.getSessionLabel());
                StringSet linkedTargets = session.getLinkedTargets();
                linkedTargets.remove(failedSnapshot.getId().toString());
                updateSessions.add(session);
            }
        }

        itemsToUpdate.addAll(updateSessions);
        itemsToDelete.addAll(failedSnapshots);
    }
}
