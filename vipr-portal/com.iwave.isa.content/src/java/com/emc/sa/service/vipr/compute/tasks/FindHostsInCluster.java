/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.host.HostRestRep;
import com.google.common.collect.Lists;

public class FindHostsInCluster extends ViPRExecutionTask<List<HostRestRep>> {
    private final URI clusterId;

    public FindHostsInCluster(URI clusterId) {
        this.clusterId = clusterId;
        provideDetailArgs(clusterId);
    }

    public FindHostsInCluster(URI clusterId, String clustername) {
        this.clusterId = clusterId;
        provideDetailArgs(clustername);
    }

    @Override
    public List<HostRestRep> executeTask() throws Exception {
        List<HostRestRep> hosts = Lists.newArrayList();
        try {
            debug(ExecutionUtils.getMessage("compute.cluster.find.cluster.debug", clusterId));
            hosts = getClient().hosts().getByCluster(clusterId);
        } catch (Exception e) {
            // catches if cluster was removed & marked for delete
            ExecutionUtils.currentContext().logError("compute.cluster.get.hosts.failed", e.getMessage());
        }
        return hosts;
    }

}
