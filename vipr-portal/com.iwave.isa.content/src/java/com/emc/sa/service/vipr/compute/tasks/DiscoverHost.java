/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.vipr.client.Task;

public class DiscoverHost extends WaitForTask<HostRestRep> {

    private URI hostId;

    public DiscoverHost(URI hostID) {
        this.hostId = hostID;
        provideDetailArgs(hostID);
    }

    @Override
    public Task<HostRestRep> doExecute() throws Exception {
        return getClient().hosts().discover(hostId);
    }
}
