package com.emc.sa.service.vipr.migration.tasks;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.vipr.client.Task;

import java.net.URI;

public class RescanHost extends WaitForTask<HostRestRep> {
    private URI host;

    public RescanHost(String host) {
        this.host = URI.create(host);
    }

    @Override
    protected Task<HostRestRep> doExecute() throws Exception {
        return getClient().hosts().rescan(host);
    }
}
