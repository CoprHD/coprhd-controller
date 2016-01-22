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
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class GetMobilityGroupVolumesByCluster extends ViPRExecutionTask<List<URI>> {
    private final VolumeGroupRestRep mobilityGroup;
    private final List<NamedRelatedResourceRep> clusters;

    public GetMobilityGroupVolumesByCluster(VolumeGroupRestRep mobilityGroup, List<NamedRelatedResourceRep> clusters) {
        this.mobilityGroup = mobilityGroup;
        this.clusters = clusters;
        provideDetailArgs(mobilityGroup, clusters);
    }

    @Override
    public List<URI> executeTask() throws Exception {
        List<URI> mobilityGroupVolumes = Lists.newArrayList();
        Set<URI> volumes = getHostExportedVolumes();

        for (URI volume : volumes) {
            VolumeRestRep vol = getClient().blockVolumes().get(volume);
            if (matchesVirtualPool(vol) && BlockStorageUtils.isVplexVolume(vol)) {
                for (RelatedResourceRep haVolume : vol.getHaVolumes()) {
                    if (matchesStorageSystem(haVolume)) {
                        mobilityGroupVolumes.add(volume);
                    }
                }
            }
        }
        return mobilityGroupVolumes;
    }

    private boolean matchesStorageSystem(RelatedResourceRep haVolume) {
        VolumeRestRep haVol = getClient().blockVolumes().get(haVolume.getId());
        return haVol.getStorageController().equals(mobilityGroup.getSourceStorageSystem());
    }

    private boolean matchesVirtualPool(VolumeRestRep vol) {
        return vol.getVirtualPool().getId() != null && vol.getVirtualPool().getId().equals(mobilityGroup.getSourceVirtualPool());
    }

    private Set<URI> getHostExportedVolumes() {
        Set<URI> volumes = Sets.newHashSet();
        for (NamedRelatedResourceRep cluster : clusters) {
            List<ExportGroupRestRep> exports = getClient().blockExports().findContainingCluster(cluster.getId());
            volumes.addAll(BlockProviderUtils.getExportedResourceIds(exports, ResourceType.VOLUME));
        }
        return volumes;
    }
}
