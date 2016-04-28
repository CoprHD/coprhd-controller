/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.VirtualPoolMapper;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.db.client.model.VirtualPool;
import com.google.common.base.Function;

public class MapFileVirtualPool implements Function<VirtualPool, FileVirtualPoolRestRep> {
    public static final MapFileVirtualPool instance = new MapFileVirtualPool();

    public static MapFileVirtualPool getInstance() {
        return instance;
    }

    private MapFileVirtualPool() {
    }

    @Override
    public FileVirtualPoolRestRep apply(VirtualPool vpool) {
        return VirtualPoolMapper.toFileVirtualPool(vpool, null);
    }
}
