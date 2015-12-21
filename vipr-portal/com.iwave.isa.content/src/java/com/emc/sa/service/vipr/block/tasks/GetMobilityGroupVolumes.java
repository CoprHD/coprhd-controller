/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.google.common.collect.Lists;

public class GetMobilityGroupVolumes extends ViPRExecutionTask<List<URI>> {
    private final URI mobilityGroup;

    public GetMobilityGroupVolumes(URI mobilityGroup) {
        this.mobilityGroup = mobilityGroup;
        provideDetailArgs(mobilityGroup);
    }

    @Override
    public List<URI> executeTask() throws Exception {
        List<URI> volumes = Lists.newArrayList();
        List<NamedRelatedResourceRep> volRefs = getClient().application().getVolumes(mobilityGroup);
        for (NamedRelatedResourceRep volume : volRefs) {
            volumes.add(volume.getId());
        }
        return volumes;
    }
}
