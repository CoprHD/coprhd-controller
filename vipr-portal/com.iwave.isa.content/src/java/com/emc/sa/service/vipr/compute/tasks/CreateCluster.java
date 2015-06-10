/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.host.cluster.ClusterCreateParam;
import com.emc.storageos.model.host.cluster.ClusterRestRep;

public class CreateCluster extends ViPRExecutionTask<ClusterRestRep> {

    private String clusterName;

    public CreateCluster(String clusterName) {
        this.clusterName = clusterName;
        provideDetailArgs(clusterName);
    }

    @Override
    public ClusterRestRep executeTask() throws Exception {
    	ClusterCreateParam clusterCreateParam = new ClusterCreateParam(clusterName);
    	return getClient().clusters().create(getOrderTenant(), clusterCreateParam);
    }
}
