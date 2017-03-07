/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.host.cluster.ClusterUpdateParam;

public class UpdateCluster extends ViPRExecutionTask<ClusterRestRep> {

    private String clusterName;
    private URI clusterID;

    public UpdateCluster(URI clusterid, String clusterName) {
        this.clusterName = clusterName;
        this.clusterID = clusterid;
        provideDetailArgs(clusterid, clusterName);
    }

    @Override
    public ClusterRestRep executeTask() throws Exception {
        ClusterUpdateParam clusterUpdateParam = new ClusterUpdateParam(clusterName);
        return getClient().clusters().update(clusterID, clusterUpdateParam);
    }
}