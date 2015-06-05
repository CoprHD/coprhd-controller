/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.ProtectionMapper;
import com.emc.storageos.model.protection.ProtectionSystemRestRep;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.google.common.base.Function;

public class MapProtectionSystem implements Function<ProtectionSystem,ProtectionSystemRestRep> {
    public static final MapProtectionSystem instance = new MapProtectionSystem();

    public static MapProtectionSystem getInstance() {
        return instance;
    }

    private MapProtectionSystem() {
    }

    @Override
    public ProtectionSystemRestRep apply(ProtectionSystem resource) {
        return ProtectionMapper.map(resource);
    }
}
