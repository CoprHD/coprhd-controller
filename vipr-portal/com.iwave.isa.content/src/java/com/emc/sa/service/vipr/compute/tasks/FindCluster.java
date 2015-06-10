/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.vipr.client.core.filters.NameFilter;
import com.emc.vipr.client.core.filters.ResourceFilter;

public class FindCluster extends ViPRExecutionTask<List<ClusterRestRep>> {
    private final String clusterName;

    public FindCluster(String clusterName) {
        this.clusterName = clusterName;
        provideDetailArgs(clusterName);
    }

    @Override
    public List<ClusterRestRep> executeTask() throws Exception {
        debug("Executing: %s", getDetail());

        ResourceFilter<ClusterRestRep> filter = new NameFilter<ClusterRestRep>(clusterName);
        return getClient().clusters().getByTenant(getOrderTenant(), filter); 	
    }
   
}
