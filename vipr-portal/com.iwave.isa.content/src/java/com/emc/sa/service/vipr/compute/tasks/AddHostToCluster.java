/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.HostUpdateParam;
import com.emc.vipr.client.Task;

public class AddHostToCluster extends WaitForTask<HostRestRep> {

	private URI host;
	private URI cluster;

    public AddHostToCluster(URI host, URI cluster) {
        this.host = host;
        this.cluster = cluster;
        provideDetailArgs(host, cluster);
    }

    @Override
    public Task<HostRestRep> doExecute() throws Exception {
    	HostUpdateParam hostUpdateParam = new HostUpdateParam();
    	hostUpdateParam.setCluster(cluster);      
        Task<HostRestRep> task = getClient().hosts().update(host,hostUpdateParam);      
        return task;
    }
}
