/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.host.ProvisionBareMetalHostsParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.vipr.client.Tasks;

/**
 * Host creation parameters
 */
public class CreateHosts extends WaitForTasks<HostRestRep> {
    private URI vcp;
    private URI cluster;
    private List<String> hostNames;
    private URI varray;

    public CreateHosts(URI vcp, URI cluster, List<String> hostNames, URI varray) {
        this.vcp = vcp;
        this.cluster = cluster;
        this.hostNames = hostNames;
        this.varray = varray;
    }

    @Override
    protected Tasks<HostRestRep> doExecute() throws Exception {
        ProvisionBareMetalHostsParam create = new ProvisionBareMetalHostsParam();
        create.setCluster(cluster);
        create.setComputeVpool(vcp);
        create.setTenant(getOrderTenant());
        create.setVarray(varray);
        for (String hostName : hostNames) {
        	if (hostName != null) {
        		create.getHostNames().add(hostName);
        	}
        }
        return getClient().hosts().provisionBareMetalHosts(create);
    }
}
