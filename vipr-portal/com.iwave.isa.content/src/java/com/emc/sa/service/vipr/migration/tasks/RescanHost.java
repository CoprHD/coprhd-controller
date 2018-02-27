package com.emc.sa.service.vipr.migration.tasks;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.vipr.client.Tasks;

import java.net.URI;

public class RescanHost extends WaitForTasks<HostRestRep> {
    private URI cgId;

    public RescanHost(String cgId) {
        this.cgId = URI.create(cgId);
    }

    @Override
    protected Tasks<HostRestRep> doExecute() throws Exception {
        return getClient().blockConsistencyGroups().rescanHostsForMigration(cgId);
    }
}
