/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.NetworkMapper;
import com.emc.storageos.model.network.NetworkSystemRestRep;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.google.common.base.Function;

public class MapNetworkSystem implements Function<NetworkSystem,NetworkSystemRestRep> {
    public static final MapNetworkSystem instance = new MapNetworkSystem();

    public static MapNetworkSystem getInstance() {
        return instance;
    }

    private MapNetworkSystem() {
    }

    @Override
    public NetworkSystemRestRep apply(NetworkSystem resource) {
        return NetworkMapper.map(resource);
    }
}
