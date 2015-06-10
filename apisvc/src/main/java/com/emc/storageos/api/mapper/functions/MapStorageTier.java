/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.model.block.tier.StorageTierRestRep;
import com.emc.storageos.db.client.model.StorageTier;
import com.google.common.base.Function;

public class MapStorageTier implements Function<StorageTier,StorageTierRestRep> {
    public static final MapStorageTier instance = new MapStorageTier();

    public static MapStorageTier getInstance() {
        return instance;
    }

    private MapStorageTier() {
    }

    @Override
    public StorageTierRestRep apply(StorageTier resource) {
        return BlockMapper.map(resource);
    }
}
