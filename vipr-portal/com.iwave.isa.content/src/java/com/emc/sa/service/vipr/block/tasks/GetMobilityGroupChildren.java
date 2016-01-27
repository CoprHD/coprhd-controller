/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.NamedVolumeGroupsList;

public class GetMobilityGroupChildren extends ViPRExecutionTask<NamedVolumeGroupsList> {
    private final URI mobilityGroup;

    public GetMobilityGroupChildren(URI mobilityGroup) {
        this.mobilityGroup = mobilityGroup;
        provideDetailArgs(mobilityGroup);
    }

    @Override
    public NamedVolumeGroupsList executeTask() throws Exception {
        return getClient().application().getChildrenVolumeGroups(mobilityGroup);
    }
}
