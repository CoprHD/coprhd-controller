/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.RemoteReplicationMapper;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupRestRep;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.google.common.base.Function;

public class MapRemoteReplicationGroup implements Function<RemoteReplicationGroup, RemoteReplicationGroupRestRep> {
    public static final MapRemoteReplicationGroup instance = new MapRemoteReplicationGroup();

    public static MapRemoteReplicationGroup getInstance() {
        return instance;
    }

    private MapRemoteReplicationGroup() {
    }

    @Override
    public RemoteReplicationGroupRestRep apply(RemoteReplicationGroup resource) {
        return RemoteReplicationMapper.map(resource);
    }
}
