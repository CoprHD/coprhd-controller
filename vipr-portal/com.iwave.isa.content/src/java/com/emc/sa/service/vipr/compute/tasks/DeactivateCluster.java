/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.vipr.client.Task;

public class DeactivateCluster extends WaitForTask<ClusterRestRep> {

	private Cluster cluster;

    public DeactivateCluster(Cluster cluster) {
        this.cluster = cluster;
        provideDetailArgs(cluster.getLabel());
    }

    @Override
    public Task<ClusterRestRep> doExecute() throws Exception {   
        Task<ClusterRestRep> task = getClient().clusters().deactivate(cluster.getId(),true); 
        return task;
    }
}
