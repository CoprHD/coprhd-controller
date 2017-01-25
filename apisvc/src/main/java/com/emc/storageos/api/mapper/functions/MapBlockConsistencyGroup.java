/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import static com.emc.storageos.api.mapper.BlockMapper.map;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.google.common.base.Function;

public class MapBlockConsistencyGroup implements Function<BlockConsistencyGroup, BlockConsistencyGroupRestRep> {
    public static final MapBlockConsistencyGroup instance = new MapBlockConsistencyGroup();

    // The DB client is required to query the list of volumes within a consistency group
    private DbClient dbClient;

    public static MapBlockConsistencyGroup getInstance(DbClient dbClient) {
        instance.setDBClient(dbClient);
        return instance;
    }

    private MapBlockConsistencyGroup() {
    }

    private void setDBClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public BlockConsistencyGroupRestRep apply(BlockConsistencyGroup consistencyGroup) {
        // If no db client, we are unable to query for the volumes
        if (dbClient == null) {
            return BlockMapper.map(consistencyGroup, null, null);
        }

        // Find all volumes assigned to the group
        final URIQueryResultList cgVolumesResults = new URIQueryResultList();
        dbClient.queryByConstraint(getVolumesByConsistencyGroup(consistencyGroup.getId()), cgVolumesResults);

        // If no volumes, just return the consistency group
        if (!cgVolumesResults.iterator().hasNext()) {
            return map(consistencyGroup, null, dbClient);
        }

        final Set<URI> volumes = new HashSet<URI>();

        while (cgVolumesResults.iterator().hasNext()) {
            volumes.add(cgVolumesResults.iterator().next());
        }

        return BlockMapper.map(consistencyGroup, volumes, dbClient);
    }
}
