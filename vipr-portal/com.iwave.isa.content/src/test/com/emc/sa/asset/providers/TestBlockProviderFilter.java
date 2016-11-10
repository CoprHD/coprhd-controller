/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.sa.asset.providers;

import static com.emc.sa.asset.providers.BlockProvider.getVolumeDetails;
import static com.emc.sa.asset.providers.BlockProvider.isInConsistencyGroup;
import static com.emc.sa.asset.providers.BlockProvider.listVolumes;
import static com.emc.sa.asset.providers.BlockProviderUtils.getVolumePersonality;
import static com.emc.sa.asset.providers.BlockProviderUtils.isLocalSnapshotSupported;
import static com.emc.sa.asset.providers.BlockProviderUtils.isRPSourceVolume;
import static com.emc.sa.asset.providers.BlockProviderUtils.isRPTargetVolume;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.emc.sa.asset.providers.BlockProvider.VolumeDetail;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;

public final class TestBlockProviderFilter {

    static void filter(ViPRCoreClient client, URI project) {
        List<VolumeRestRep> volumes = listVolumes(client, project);
        List<VolumeDetail> volumeDetails = getVolumeDetails(client, volumes);
        Map<URI, VolumeRestRep> volumeNames = ResourceUtils.mapById(volumes);

        List<AssetOption> options = Lists.newArrayList();
        for (VolumeDetail detail : volumeDetails) {

            boolean localSnapSupported = isLocalSnapshotSupported(detail.vpool);
            boolean isRPTargetVolume = isRPTargetVolume(detail.volume);
            boolean isRPSourceVolume = isRPSourceVolume(detail.volume);
            boolean isInConsistencyGroup = isInConsistencyGroup(detail.volume);

            if (isRPSourceVolume || (localSnapSupported && (!isInConsistencyGroup || isRPTargetVolume))) {
                options.add(BlockProvider.createVolumeOption(client, null, detail.volume, volumeNames));
                System.out.println("\t* " + detail.volume.getName());
            } else {
                System.out.println("\t" + detail.volume.getName());
            }

            String extra = String.format(
                    "[localSnapSupported: %s, isRPTargetVolume: %s, isRPSourceVolume: %s, isInConsistencyGroup: %s]",
                    localSnapSupported,
                    isRPTargetVolume, isRPSourceVolume, isInConsistencyGroup);

            System.out.println(String.format("\t\tpersonality:[ %s ], filter:[ %s ]", getVolumePersonality(detail.volume), extra));
        }
    }

    public static void main(String[] args) throws URISyntaxException {
        Logger.getRootLogger().setLevel(Level.OFF);
        ViPRCoreClient client =
                new ViPRCoreClient("host", true).withLogin("root", "password");
        try {

            for (ProjectRestRep project : client.projects().getByUserTenant()) {
                System.out.println("Project : " + project.getName());
                filter(client, project.getId());
                System.out.println();
            }

        } finally {
            client.auth().logout();
        }
    }
}
