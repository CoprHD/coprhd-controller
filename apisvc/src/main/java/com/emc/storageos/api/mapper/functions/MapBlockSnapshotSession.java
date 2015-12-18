/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.google.common.base.Function;

/**
 * Implements a function that maps a BlockSnapshotSession instance to its Rest response.
 */
public class MapBlockSnapshotSession implements Function<BlockSnapshotSession, BlockSnapshotSessionRestRep> {

    // The singleton instance.
    public static final MapBlockSnapshotSession instance = new MapBlockSnapshotSession();

    // A reference to a database client.
    private DbClient dbClient;

    /**
     * Function creates if necessary and returns the singleton instance.
     * 
     * @param dbClient A reference to a database client.
     * 
     * @return The singleton MapBlockSnapshotSession
     */
    public static MapBlockSnapshotSession getInstance(DbClient dbClient) {
        instance.setDBClient(dbClient);
        return instance;
    }

    /**
     * Private default constructor.
     */
    private MapBlockSnapshotSession() {
    }

    /**
     * Setter for the database client.
     * 
     * @param dbClient A reference to a database client.
     */
    private void setDBClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlockSnapshotSessionRestRep apply(BlockSnapshotSession snapSession) {
        return BlockMapper.map(dbClient, snapSession);
    }
}