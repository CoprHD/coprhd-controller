/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.rollback;

import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.util.CustomQueryUtility;

import java.net.URI;
import java.util.Collection;
import java.util.List;

/**
 * Implementation of {@link ReplicaCleanup} for deleting failed instances of {@link BlockSnapshotSession} that
 * were temporarily created for adding linked targets when expanding a consistency group.
 */
public class BlockSnapshotSessionCleanup extends ReplicaCleanup {

    /**
     * If the given volume was part of a consistency group expand request, it would have had a temporary
     * {@link BlockSnapshotSession} created for synchronization with a new linked target.  This method
     * will find those sessions and add them for deletion.
     *
     * @param volume        Volume URI to process.
     * @param itemsToUpdate Items to be updated.
     * @param itemsToDelete Items to be deleted.
     */
    @Override
    public void process(URI volume, Collection<DataObject> itemsToUpdate, Collection<DataObject> itemsToDelete) {
        List<BlockSnapshotSession> sessions = CustomQueryUtility.queryActiveResourcesByConstraint(getDbClient(),
                BlockSnapshotSession.class,
                ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(volume));
        itemsToDelete.addAll(sessions);
    }
}
