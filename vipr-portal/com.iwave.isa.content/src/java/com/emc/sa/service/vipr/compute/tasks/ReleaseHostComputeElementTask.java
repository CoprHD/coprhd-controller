/*
 * Copyright (c) 2016-2017
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.vipr.client.Task;

public class ReleaseHostComputeElementTask extends WaitForTask<HostRestRep> {
    private URI hostURI;

    public ReleaseHostComputeElementTask(URI host) {
        this.hostURI = host;
        provideDetailArgs(host);
    }

    @Override
    protected Task<HostRestRep> doExecute() throws Exception {
        Task<HostRestRep> task = getClient().hosts().releaseHostComputeElement(hostURI);
        addOrderIdTag(task.getTaskResource().getId());
        return task;
    }

}