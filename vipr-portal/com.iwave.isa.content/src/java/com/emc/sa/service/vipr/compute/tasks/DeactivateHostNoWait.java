/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.vipr.client.Task;

public class DeactivateHostNoWait extends ViPRExecutionTask<Task<HostRestRep>> {

    private URI hostId;
    private boolean detachStorage;

    public DeactivateHostNoWait(Host host) {
        this.hostId = host.getId();
        provideDetailArgs(host.getHostName());
    }

    public DeactivateHostNoWait(URI hostID) {
        this.hostId = hostID;
        provideDetailArgs(hostID);
    }

    public DeactivateHostNoWait(Host host, boolean detachStorage) {
        this.hostId = host.getId();
        this.detachStorage = detachStorage;
        provideDetailArgs(host.getHostName());
    }

    public DeactivateHostNoWait(URI hostID, boolean detachStorage) {
        this.hostId = hostID;
        this.detachStorage = detachStorage;
        provideDetailArgs(hostID);
    }

    public DeactivateHostNoWait(URI hostID, String hostname, boolean detachStorage) {
        this.hostId = hostID;
        this.detachStorage = detachStorage;
        provideDetailArgs(hostname);
    }

    @Override
    public Task<HostRestRep> executeTask() throws Exception {
        Task<HostRestRep> task = getClient().hosts().deactivate(hostId, detachStorage);
        addOrderIdTag(task.getTaskResource().getId());
        return task;
    }
}