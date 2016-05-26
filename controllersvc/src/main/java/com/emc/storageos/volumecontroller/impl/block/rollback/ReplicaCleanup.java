/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.rollback;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;

import java.net.URI;
import java.util.Collection;

/**
 * Abstract class to be subclassed for providing specific replica cleanup processing.
 */
public abstract class ReplicaCleanup {

    private DbClient dbClient;

    /**
     * Getter for the database client.
     *
     * @return  The Database client.
     */
    public DbClient getDbClient() {
        return dbClient;
    }

    /**
     * Setter for the database client.
     *
     * @param dbClient  Database client.
     */
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * Abstract method to be overridden for providing specific replica cleanup processing.  Any replicas found to be
     * deleted or updated in the database should be added to appropriate collection argument.
     *
     * @param volume        Volume URI to process.
     * @param itemsToUpdate Items to be updated.
     * @param itemsToDelete Items to be deleted.
     */
    public abstract void process(URI volume, Collection<DataObject> itemsToUpdate, Collection<DataObject> itemsToDelete);
}
