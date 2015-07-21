/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.ComputeMapper;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.model.compute.ComputeSystemRestRep;
import com.google.common.base.Function;

public class MapComputeSystem implements Function<ComputeSystem,ComputeSystemRestRep> {
    public static final MapComputeSystem instance = new MapComputeSystem();

    public static MapComputeSystem getInstance() {
        return instance;
    }

    private MapComputeSystem() {
    }

    @Override
    public ComputeSystemRestRep apply(ComputeSystem resource) {
        return ComputeMapper.map(resource);
    }
}
