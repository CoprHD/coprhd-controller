/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.application.VolumeGroupRestRep;

public class GetMobilityGroup extends ViPRExecutionTask<VolumeGroupRestRep> {
    private final URI mobilityGroupId;

    public GetMobilityGroup(String mobilityGroupId) {
        this(uri(mobilityGroupId));
    }

    public GetMobilityGroup(URI mobilityGroupId) {
        this.mobilityGroupId = mobilityGroupId;
        provideDetailArgs(mobilityGroupId);
    }

    @Override
    public VolumeGroupRestRep executeTask() throws Exception {
        return getClient().application().get(mobilityGroupId);
    }
}
