/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.SystemsMapper;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.google.common.base.Function;

public class MapStorageSystem implements Function<StorageSystem,StorageSystemRestRep> {
    public static final MapStorageSystem instance = new MapStorageSystem();

    public static MapStorageSystem getInstance() {
        return instance;
    }

    private MapStorageSystem() {
    }

    @Override
    public StorageSystemRestRep apply(StorageSystem resource) {
        return SystemsMapper.map(resource);
    }
}
