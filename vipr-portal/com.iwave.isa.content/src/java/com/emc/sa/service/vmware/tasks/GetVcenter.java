/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vmware.tasks;

import java.net.URI;

import javax.inject.Inject;

import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.model.host.vcenter.VcenterRestRep;

public class GetVcenter extends ViPRExecutionTask<Vcenter> {
    @Inject
    private ModelClient models;
    private URI id;

    public GetVcenter(URI id) {
        this.id = id;
        provideDetailArgs(id);
    }

    @Override
    public Vcenter executeTask() throws Exception {
        // Verify that the vcenter exists through the REST api, and that we have permission to access it
        VcenterRestRep rep = getClient().vcenters().get(id);
        if (rep == null) {
            return null;
        }
        return models.of(Vcenter.class).findById(id);
    }
}
