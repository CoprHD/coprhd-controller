/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.rollback;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;

/**
 * A context class composed of {@link ReplicaCleanup} instances that deal
 * with the cleanup of various replicas as part of rollback.
 */
public class ReplicaCleanupContext {

    private DbClient dbClient;
    private Collection<ReplicaCleanup> cleanups;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setCleanups(Collection<ReplicaCleanup> cleanups) {
        this.cleanups = cleanups;
    }

    /**
     * Iterates over the collection of volume URIs, having each {@link ReplicaCleanup} instance
     * process each member, adding relevant items for either deletion or updating.
     *
     * Finally, call the database to make the necessary changes.
     *
     * @param volumes   Collection of Volume URIs.
     */
    public void execute(Collection<URI> volumes) {

        if (volumes == null || volumes.isEmpty()) {
            return;
        }

        Collection<DataObject> itemsToUpdate = new HashSet<>();
        Collection<DataObject> itemsToDelete = new HashSet<>();

        for (URI volume : volumes) {
            for (ReplicaCleanup cleanup : cleanups) {
                cleanup.process(volume, itemsToUpdate, itemsToDelete);
            }
        }

        if (!itemsToDelete.isEmpty()) {
            dbClient.markForDeletion(itemsToDelete);
        }

        if (!itemsToUpdate.isEmpty()) {
            dbClient.updateObject(itemsToUpdate);
        }
    }

}
