/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.ProtectionMapper;
import com.emc.storageos.model.protection.ProtectionSetRestRep;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.google.common.base.Function;

public class MapProtectionSet implements Function<ProtectionSet,ProtectionSetRestRep> {
    public static final MapProtectionSet instance = new MapProtectionSet();

    public static MapProtectionSet getInstance() {
        return instance;
    }

    private MapProtectionSet() {
    }

    @Override
    public ProtectionSetRestRep apply(ProtectionSet resource) {
        return ProtectionMapper.map(resource);
    }
}
