/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.ProvisionBareMetalHostsParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;

/**
 * Host creation parameters
 */
public class CreateHosts extends ViPRExecutionTask<Tasks<HostRestRep>> {
    private URI vcp;
    private URI cluster;
    private List<String> hostNames;
    private URI varray;
    private URI sptId;

    public CreateHosts(URI vcp, URI cluster, List<String> hostNames, URI varray, URI sptId) {
        this.vcp = vcp;
        this.cluster = cluster;
        this.hostNames = hostNames;
        this.varray = varray;
        this.sptId = sptId;
    }

    @Override
    public Tasks<HostRestRep> executeTask() throws Exception {
        ProvisionBareMetalHostsParam create = new ProvisionBareMetalHostsParam();
        create.setCluster(cluster);
        create.setComputeVpool(vcp);
        create.setTenant(getOrderTenant());
        create.setVarray(varray);
        create.setServiceProfileTemplate(sptId);
        for (String hostName : hostNames) {
            if (hostName != null) {
                create.getHostNames().add(hostName);
            }
        }
        Tasks<HostRestRep> tasks = getClient().hosts().provisionBareMetalHosts(create);
        for (Task<HostRestRep> task : tasks.getTasks()) {
            addOrderIdTag(task.getTaskResource().getId());
        }
        return tasks;
    }

}
