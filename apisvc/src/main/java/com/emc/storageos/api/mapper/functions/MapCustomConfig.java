/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.db.client.model.CustomConfig;
import com.emc.storageos.model.customconfig.CustomConfigRestRep;
import com.google.common.base.Function;

public class MapCustomConfig implements Function<CustomConfig, CustomConfigRestRep>{

    public static final MapCustomConfig instance = new MapCustomConfig();

    public static MapCustomConfig getInstance() {
        return instance;
    }

    private MapCustomConfig() {
    }

    @Override
    public CustomConfigRestRep apply(CustomConfig resource) {
        return DbObjectMapper.map(resource);
    }
}
