/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.vipr.client.Task;

public class DeactivateHost extends WaitForTask<HostRestRep> {

    private URI hostId;
    private boolean detachStorage;

    public DeactivateHost(Host host) {
        this.hostId = host.getId();
        provideDetailArgs(host.getHostName());
    }

    public DeactivateHost(URI hostID) {
        this.hostId = hostID;
        provideDetailArgs(hostID);
    }

    public DeactivateHost(Host host, boolean detachStorage) {
        this.hostId = host.getId();
        this.detachStorage = detachStorage;
        provideDetailArgs(host.getHostName());
    }

    public DeactivateHost(URI hostID, boolean detachStorage) {
        this.hostId = hostID;
        this.detachStorage = detachStorage;
        provideDetailArgs(hostID);
    }

    public DeactivateHost(URI hostID, String hostname, boolean detachStorage) {
        this.hostId = hostID;
        this.detachStorage = detachStorage;
        provideDetailArgs(hostname);
    }

    @Override
    public Task<HostRestRep> doExecute() throws Exception {
        Task<HostRestRep> task = getClient().hosts().deactivate(hostId, detachStorage);
        addOrderIdTag(task.getTaskResource().getId());
        return task;
    }
}
