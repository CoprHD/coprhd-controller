/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.model.compute.VcenterClusterParam;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterRestRep;
import com.emc.vipr.client.Task;

public class UpdateVcenterCluster extends WaitForTask<VcenterDataCenterRestRep> {

    private URI cluster;
    private URI datacenter;

    public UpdateVcenterCluster(URI cluster, URI datacenter) {
        this.cluster = cluster;
        this.datacenter = datacenter;
        provideDetailArgs(cluster, datacenter);
    }

    public UpdateVcenterCluster(Cluster cluster, VcenterDataCenter datacenter) {
        this.cluster = cluster.getId();
        this.datacenter = datacenter.getId();
        provideDetailArgs(cluster.getLabel(), datacenter.getLabel());
    }

    @Override
    public Task<VcenterDataCenterRestRep> doExecute() throws Exception {
        VcenterClusterParam clusterParam = new VcenterClusterParam(cluster);
        Task<VcenterDataCenterRestRep> task = getClient().vcenterDataCenters().updateVcenterCluster(datacenter, clusterParam);
        return task;
    }
}
