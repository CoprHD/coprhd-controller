/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.RemoteReplicationMapper;
import com.emc.storageos.model.remotereplication.RemoteReplicationSetRestRep;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet;
import com.google.common.base.Function;

public class MapRemoteReplicationSet implements Function<RemoteReplicationSet, RemoteReplicationSetRestRep> {
    public static final MapRemoteReplicationSet instance = new MapRemoteReplicationSet();

    public static MapRemoteReplicationSet getInstance() {
        return instance;
    }

    private MapRemoteReplicationSet() {
    }

    @Override
    public RemoteReplicationSetRestRep apply(RemoteReplicationSet resource) {
        return RemoteReplicationMapper.map(resource);
    }
}
