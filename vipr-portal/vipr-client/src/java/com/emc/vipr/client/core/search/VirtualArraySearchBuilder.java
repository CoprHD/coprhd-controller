/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.search;

import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.vipr.client.core.AbstractResources;
import java.net.URI;
import static com.emc.vipr.client.core.impl.SearchConstants.*;

public class VirtualArraySearchBuilder extends SearchBuilder<VirtualArrayRestRep> {
    public VirtualArraySearchBuilder(AbstractResources<VirtualArrayRestRep> resources) {
        super(resources);
    }

    public SearchBuilder<VirtualArrayRestRep> byInitiatorPort(String initiatorPort) {
        return by(INITIATOR_PORT_PARAM, initiatorPort);
    }

    public SearchBuilder<VirtualArrayRestRep> byHost(String hostId) {
        return by(HOST_PARAM, hostId);
    }

    public SearchBuilder<VirtualArrayRestRep> byHost(URI hostId) {
        return byHost(hostId.toString());
    }

    public SearchBuilder<VirtualArrayRestRep> byCluster(String clusterId) {
        return by(CLUSTER_PARAM, clusterId);
    }

    public SearchBuilder<VirtualArrayRestRep> byCluster(URI clusterId) {
        return byCluster(clusterId.toString());
    }
}
