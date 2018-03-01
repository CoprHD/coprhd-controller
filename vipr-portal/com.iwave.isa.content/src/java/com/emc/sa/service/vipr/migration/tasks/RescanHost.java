/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */

package com.emc.sa.service.vipr.migration.tasks;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.vipr.client.Tasks;

import java.net.URI;

public class RescanHost extends WaitForTasks<HostRestRep> {
    private URI sgId;

    public RescanHost(String sgId) {
        this.sgId = URI.create(sgId);
    }

    @Override
    protected Tasks<HostRestRep> doExecute() throws Exception {
        return getClient().blockConsistencyGroups().rescanHostsForMigration(sgId);
    }
}
