/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.NamedVolumeGroupsList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class GetMobilityGroupVolumes extends ViPRExecutionTask<Set<URI>> {
    private final List<URI> mobilityGroups = Lists.newArrayList();

    public GetMobilityGroupVolumes(List<URI> mobilityGroups) {
        this.mobilityGroups.addAll(mobilityGroups);
        provideDetailArgs(mobilityGroups);
    }

    public GetMobilityGroupVolumes(NamedVolumeGroupsList children) {
        for (NamedRelatedResourceRep child : children.getVolumeGroups()) {
            mobilityGroups.add(child.getId());
        }
        provideDetailArgs(mobilityGroups);
    }

    @Override
    public Set<URI> executeTask() throws Exception {
        Set<URI> volumes = Sets.newHashSet();
        for (URI mobilityGroup : mobilityGroups) {
            List<NamedRelatedResourceRep> volRefs = getClient().application().getVolumes(mobilityGroup);
            for (NamedRelatedResourceRep volume : volRefs) {
                volumes.add(volume.getId());
            }
        }
        return volumes;
    }
}
