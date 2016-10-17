/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.rollback;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshotSession;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

/**
 * Factory class to build a {@link ReplicaCleanupContext}.
 */
public class ReplicaCleanupFactory {

    private ReplicaCleanupFactory() {
    }

    /**
     * Build an instance of {@link ReplicaCleanupContext} for cleaning up
     * failed BlockSnapshots, failed BlockSnapshotSessions and stale entries
     * in any {@link BlockSnapshotSession#getLinkedTargets()} fields.
     *
     * @param dbClient  The Database Client.
     * @return          An initialized instance of ReplicaCleanupContext.
     */
    public static ReplicaCleanupContext getContext(DbClient dbClient) {
        ReplicaCleanupContext ctx = new ReplicaCleanupContext();

        ctx.setDbClient(dbClient);

        // List of ReplicaCleanup instances.
        List<ReplicaCleanup> replicaCleanups = newArrayList();
        replicaCleanups.add(new BlockSnapshotCleanup());
        replicaCleanups.add(new BlockSnapshotSessionCleanup());
        // Add new classes here...

        for (ReplicaCleanup replicaCleanup : replicaCleanups) {
            replicaCleanup.setDbClient(dbClient);
        }
        ctx.setCleanups(replicaCleanups);

        return ctx;
    }
}
