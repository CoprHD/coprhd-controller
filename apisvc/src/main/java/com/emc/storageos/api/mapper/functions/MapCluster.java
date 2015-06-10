/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.HostMapper;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.google.common.base.Function;

public class MapCluster implements Function<Cluster,ClusterRestRep>{
	public static final MapCluster instance = new MapCluster();

    public static MapCluster getInstance() {
        return instance;
    }

    private MapCluster() {
    }

    @Override
    public ClusterRestRep apply(Cluster resource) {
        return HostMapper.map(resource);
    }
}
