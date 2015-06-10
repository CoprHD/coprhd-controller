/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.model.block.UnManagedVolumeRestRep;
import com.google.common.base.Function;

public class MapUnmanagedVolume implements Function<UnManagedVolume,UnManagedVolumeRestRep> {
    public static final MapUnmanagedVolume instance = new MapUnmanagedVolume();

    public static MapUnmanagedVolume getInstance() {
        return instance;
    }

    private MapUnmanagedVolume() {
    }

    @Override
    public UnManagedVolumeRestRep apply(UnManagedVolume volume) {
        return BlockMapper.map(volume);
    }
}
