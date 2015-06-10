/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.HostMapper;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.host.HostRestRep;
import com.google.common.base.Function;

public class MapHost implements Function<Host, HostRestRep>{
	public static final MapHost instance = new MapHost();

    public static MapHost getInstance() {
        return instance;
    }

    private MapHost() {
    }

    @Override
    public HostRestRep apply(Host resource) {
        return HostMapper.map(resource);
    }
}
