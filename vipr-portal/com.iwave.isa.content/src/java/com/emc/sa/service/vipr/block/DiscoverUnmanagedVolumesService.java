/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.STORAGE_SYSTEMS;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.DiscoverUnmanagedVolumes;
import com.emc.sa.service.vipr.block.tasks.GetUnmanagedVolumesForStorageSystem;
import com.emc.sa.service.vipr.tasks.GetStorageSystems;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;

@Service("DiscoverUnmanagedVolumes")
public class DiscoverUnmanagedVolumesService extends ViPRService {
    @Param(STORAGE_SYSTEMS)
    protected List<String> storageSystems;

    @Override
    public void execute() throws Exception {

        List<URI> uris = uris(storageSystems);

        List<StorageSystemRestRep> systemRestReps =
                execute(new GetStorageSystems(uris));

        for (StorageSystemRestRep storageSystem : systemRestReps) {

            logInfo("discover.unmanaged.volume.service.discovering", storageSystem.getName());

            execute(new DiscoverUnmanagedVolumes(storageSystem.getId().toString()));

            int postCount = countUnmanagedVolumes(storageSystem.getId().toString());
            logInfo("discover.unmanaged.volume.service.discovered", postCount, storageSystem.getName());

        }

    }

    private int countUnmanagedVolumes(String storageSystem) {
        int total = 0;

        List<RelatedResourceRep> unmanaged =
                execute(new GetUnmanagedVolumesForStorageSystem(storageSystem));
        if (unmanaged != null) {
            total = unmanaged.size();
        }
        return total;
    }
}
