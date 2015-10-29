/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.VirtualPoolMapper;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.vpool.ObjectVirtualPoolRestRep;
import com.google.common.base.Function;

public final class MapObjectVirtualPool implements Function<VirtualPool, ObjectVirtualPoolRestRep> {
    public static final MapObjectVirtualPool instance = new MapObjectVirtualPool();

    public static MapObjectVirtualPool getInstance() {
        return instance;
    }

    private MapObjectVirtualPool() {
    }

    @Override
    public ObjectVirtualPoolRestRep apply(VirtualPool vpool) {
        return VirtualPoolMapper.toObjectVirtualPool(vpool);
    }
}
