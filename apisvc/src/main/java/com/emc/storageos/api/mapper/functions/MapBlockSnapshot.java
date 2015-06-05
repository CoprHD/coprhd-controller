/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.google.common.base.Function;

public class MapBlockSnapshot implements Function<BlockSnapshot,BlockSnapshotRestRep> {
    public static final MapBlockSnapshot instance = new MapBlockSnapshot();
    private DbClient dbClient;

    public static MapBlockSnapshot getInstance(DbClient dbClient) {
        instance.setDBClient(dbClient);
        return instance;
    }

    private MapBlockSnapshot() {
    }

    private void setDBClient(DbClient dbClient){
        this.dbClient = dbClient;
    }

    @Override
    public BlockSnapshotRestRep apply(BlockSnapshot blockSnapshot) {
        return BlockMapper.map(dbClient, blockSnapshot);
    }
}
