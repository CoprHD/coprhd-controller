/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.tasks;

import java.net.URI;

import javax.inject.Inject;

import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterRestRep;

public class GetVcenterDataCenter extends ViPRExecutionTask<VcenterDataCenter> {
    @Inject
    private ModelClient models;
    private URI id;

    public GetVcenterDataCenter(URI id) {
        this.id = id;
        provideDetailArgs(id);
    }

    @Override
    public VcenterDataCenter executeTask() throws Exception {
        // Verify that the object exists through the REST api, and that we have permission to access it
        VcenterDataCenterRestRep rep = getClient().vcenterDataCenters().get(id);
        if (rep == null) {
            return null;
        }
        return models.of(VcenterDataCenter.class).findById(id);
    }
}
