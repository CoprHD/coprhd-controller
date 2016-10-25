/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.block.VolumeRestRep;
import com.google.common.base.Function;

public class MapVolume implements Function<Volume, VolumeRestRep> {
    public static MapVolume instance;
    private DbClient dbClient;
    private Map<URI, StorageSystem> storageSystemCache;
    // A map of project URI to a boolean indicating whether project is SRDF capable
    private Map<URI, Boolean> projectSrdfCapableCache;
    
    public static MapVolume getInstance() {
        if (instance == null) {
            instance = new MapVolume();
        }
        return instance;
    }
    
    // This method is supposed to be used by Volume Bulk API only.
    // An in-memory cache for StorageSystem table is allocated for each MapVolume instance and
    // we want to release the cache at the end of bulk API. So we create MapVolume instance 
    // here so that JVM GC can release it when bulk API finishes.
    public static MapVolume getInstance(DbClient dbClient) {
       return new MapVolume(dbClient);
    }

    private MapVolume() {
    }
    
    private MapVolume(DbClient dbClient) {
        this.dbClient = dbClient;
        this.storageSystemCache = new HashMap<URI, StorageSystem>();
        this.projectSrdfCapableCache = new HashMap<URI, Boolean>();
    }

    @Override
    public VolumeRestRep apply(Volume volume) {
        // Via this mechanism, the volume rest rep will not contain target varrays or other "deep dive" objects within the volume
        return BlockMapper.map(dbClient, volume, storageSystemCache, projectSrdfCapableCache);
    }
}
