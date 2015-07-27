/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.HostUpdateParam;
import com.emc.vipr.client.Task;

public class RemoveHostFromCluster extends WaitForTask<HostRestRep> {

	private URI host;

    public RemoveHostFromCluster(URI host) {
        this.host = host;
        provideDetailArgs(host);
    }

    @Override
    public Task<HostRestRep> doExecute() throws Exception {
    	HostUpdateParam hostUpdateParam = new HostUpdateParam();
    	hostUpdateParam.setCluster(uri("null"));      
        Task<HostRestRep> task = getClient().hosts().update(host,hostUpdateParam);      
        return task;
    }
}
