/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import java.net.URI;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.vipr.client.Task;

public class UpdateClusterExports extends ViPRExecutionTask<Task<ClusterRestRep>> {

    private String clusterName;
    private URI clusterID;

    public UpdateClusterExports(URI clusterid, String clusterName) {
        this.clusterName = clusterName;
        this.clusterID = clusterid;
        provideDetailArgs(this.clusterID, this.clusterName);
    }

    @Override
    public Task<ClusterRestRep> executeTask() throws Exception {
        Task<ClusterRestRep> task = getClient().clusters().updateExportGroups(clusterID);
        addOrderIdTag(task.getTaskResource().getId());
        return task;
    }
}