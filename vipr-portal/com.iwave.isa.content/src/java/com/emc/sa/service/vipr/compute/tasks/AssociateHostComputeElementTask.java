/*
 * Copyright (c) 2016-2017
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.host.AssociateHostComputeElementParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.vipr.client.Task;

public class AssociateHostComputeElementTask extends WaitForTask<HostRestRep> {
    private URI hostURI;

    private AssociateHostComputeElementParam param = null;

    public AssociateHostComputeElementTask(URI host, URI associateHostComputeVPool, URI associateHostComputeElement, URI computeSystemId) {
        this.hostURI = host;
        provideDetailArgs(host);
        param = new AssociateHostComputeElementParam();
        param.setComputeElementId(associateHostComputeElement);
        param.setComputeSystemId(computeSystemId);
        param.setComputeVPoolId(associateHostComputeVPool);
    }

    @Override
    protected Task<HostRestRep> doExecute() throws Exception {
        Task<HostRestRep> task = getClient().hosts().associateHostComputeElement(hostURI, param);
        addOrderIdTag(task.getTaskResource().getId());
        return task;
    }
}