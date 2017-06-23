/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.RemoteReplicationMapper;
import com.emc.storageos.model.remotereplication.RemoteReplicationPairRestRep;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.google.common.base.Function;

public class MapRemoteReplicationPair implements Function<RemoteReplicationPair, RemoteReplicationPairRestRep> {
    public static final MapRemoteReplicationPair instance = new MapRemoteReplicationPair();

    public static MapRemoteReplicationPair getInstance() {
        return instance;
    }

    private MapRemoteReplicationPair() {
    }

    @Override
    public RemoteReplicationPairRestRep apply(RemoteReplicationPair resource) {
        return RemoteReplicationMapper.map(resource);
    }
}
