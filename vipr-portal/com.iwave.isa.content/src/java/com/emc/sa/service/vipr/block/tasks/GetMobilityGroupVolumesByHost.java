/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.emc.sa.asset.providers.BlockProviderUtils;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.google.common.collect.Sets;

public class GetMobilityGroupVolumesByHost extends ViPRExecutionTask<Set<URI>> {
    private final VolumeGroupRestRep mobilityGroup;
    private final List<NamedRelatedResourceRep> hosts;

    public GetMobilityGroupVolumesByHost(VolumeGroupRestRep mobilityGroup, List<NamedRelatedResourceRep> hosts) {
        this.mobilityGroup = mobilityGroup;
        this.hosts = hosts;
        provideDetailArgs(mobilityGroup, hosts);
    }

    @Override
    public Set<URI> executeTask() throws Exception {
        Set<URI> mobilityGroupVolumes = Sets.newHashSet();
        Set<URI> volumes = getHostExportedVolumes();

        for (URI volume : volumes) {
            VolumeRestRep vol = getClient().blockVolumes().get(volume);
            if (BlockStorageUtils.isVplexVolume(vol)) {
                // for (RelatedResourceRep haVolume : vol.getHaVolumes()) {
                // if (matchesStorageSystem(haVolume)) {
                mobilityGroupVolumes.add(volume);
                // }
                // }
            }
        }
        return mobilityGroupVolumes;
    }

    private Set<URI> getHostExportedVolumes() {
        Set<URI> volumes = Sets.newHashSet();
        for (NamedRelatedResourceRep host : hosts) {
            List<ExportGroupRestRep> exports = getClient().blockExports().findContainingHost(host.getId());
            volumes.addAll(BlockProviderUtils.getExportedResourceIds(exports, ResourceType.VOLUME));
        }
        return volumes;
    }
}
