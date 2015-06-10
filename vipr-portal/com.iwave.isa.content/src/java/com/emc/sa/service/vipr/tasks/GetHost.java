/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import java.net.URI;

import javax.inject.Inject;

import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.host.HostRestRep;

public class GetHost extends ViPRExecutionTask<Host> {
    @Inject
    private ModelClient models;
    private URI id;

    public GetHost(URI id) {
        this.id = id;
        provideDetailArgs(id);
    }

    @Override
    public Host executeTask() throws Exception {
        // Verify that the object exists through the REST api, and that we have permission to access it
        HostRestRep rep = getClient().hosts().get(id);
        if (rep == null) {
            return null;
        }
        return models.of(Host.class).findById(id);
    }
}
