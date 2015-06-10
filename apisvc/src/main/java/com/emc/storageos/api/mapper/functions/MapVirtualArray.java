/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.VirtualArrayMapper;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.db.client.model.VirtualArray;
import com.google.common.base.Function;

public class MapVirtualArray implements Function<VirtualArray,VirtualArrayRestRep> {
    public static final MapVirtualArray instance = new MapVirtualArray();

    public static MapVirtualArray getInstance() {
        return instance;
    }

    private MapVirtualArray() {
    }

    @Override
    public VirtualArrayRestRep apply(VirtualArray resource) {
        return VirtualArrayMapper.map(resource);
    }
}
