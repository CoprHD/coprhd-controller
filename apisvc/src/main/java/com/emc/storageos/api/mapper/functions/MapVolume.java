/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import java.util.Map;
import java.util.HashMap;
import java.net.URI;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.model.block.VolumeRestRep;
import com.google.common.base.Function;

public class MapVolume implements Function<Volume, VolumeRestRep> {
    public static MapVolume instance;
    private DbClient dbClient;
    // A map of project URI to a boolean indicating whether project is SRDF capable
    private Map<URI, Boolean> projectSrdfCapableCache;
    
    public static MapVolume getInstance() {
        if (instance == null) {
            instance = new MapVolume();
        }
        return instance;
    }
    
    public static MapVolume getInstance(DbClient dbClient) {
        if (instance == null) {
            instance = new MapVolume(dbClient);
        }
        return instance;
    }

    private MapVolume() {
    }
    
    private MapVolume(DbClient dbClient) {
        this.dbClient = dbClient;
        this.projectSrdfCapableCache = new HashMap<URI, Boolean>();
    }

    @Override
    public VolumeRestRep apply(Volume volume) {
        // Via this mechanism, the volume rest rep will not contain target varrays or other "deep dive" objects within the volume
        return BlockMapper.map(dbClient, volume, projectSrdfCapableCache);
    }
}
