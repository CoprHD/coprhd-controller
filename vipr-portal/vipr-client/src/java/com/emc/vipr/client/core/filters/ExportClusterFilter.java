/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;

import java.net.URI;

public class ExportClusterFilter extends ExportFilter {
    private final URI clusterId;

    public ExportClusterFilter(URI clusterId, URI projectId, URI varrayId) {
        super(projectId, varrayId);
        this.clusterId = clusterId;
    }

    @Override
    public boolean accept(ExportGroupRestRep item) {
        if (!super.accept(item)) {
            return false;
        }

        return item.getType().equals("Cluster") && hasCluster(item);
    }

    private boolean hasCluster(ExportGroupRestRep item) {
        return ResourceUtils.find(item.getClusters(), clusterId) != null;
    }
}
