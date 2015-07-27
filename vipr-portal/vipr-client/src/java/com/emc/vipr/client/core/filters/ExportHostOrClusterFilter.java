/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;

import java.net.URI;

/**
 * Filter for matching export groups based on host or cluster.
 */
public class ExportHostOrClusterFilter extends DefaultResourceFilter<ExportGroupRestRep> {
    private final URI hostId;
    private final URI clusterId;

    public ExportHostOrClusterFilter(URI hostId, URI clusterId) {
        this.hostId = hostId;
        this.clusterId = clusterId;
    }

    @Override
    public boolean accept(ExportGroupRestRep item) {
        return hasHost(item) || hasCluster(item);
    }

    private boolean hasHost(ExportGroupRestRep item) {
        return ResourceUtils.find(item.getHosts(), hostId) != null;
    }

    private boolean hasCluster(ExportGroupRestRep item) {
        return ResourceUtils.find(item.getClusters(), clusterId) != null;
    }
}
