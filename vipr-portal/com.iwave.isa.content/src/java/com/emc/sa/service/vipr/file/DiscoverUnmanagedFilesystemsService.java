/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.STORAGE_SYSTEMS;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.file.tasks.DiscoverUnmanagedFilesystems;
import com.emc.sa.service.vipr.file.tasks.GetUnmanagedFilesystemsForStorageSystem;
import com.emc.sa.service.vipr.tasks.GetStorageSystems;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;

@Service("DiscoverUnmanagedFilesystems")
public class DiscoverUnmanagedFilesystemsService extends ViPRService {
    @Param(STORAGE_SYSTEMS)
    protected List<String> storageSystems;

    @Override
    public void execute() throws Exception {

        List<URI> uris = uris(storageSystems);

        List<StorageSystemRestRep> systemRestReps =
                execute(new GetStorageSystems(uris));

        for (StorageSystemRestRep storageSystem : systemRestReps) {

            logInfo("discover.unmanaged.filesystem.service.discovering", storageSystem.getName());

            execute(new DiscoverUnmanagedFilesystems(storageSystem.getId().toString()));

            int postCount = countUnmanagedFileSystems(storageSystem.getId().toString());
            logInfo("discover.unmanaged.filesystem.service.discovered", postCount, storageSystem.getName());

        }

    }

    private int countUnmanagedFileSystems(String storageSystem) {
        int total = 0;

        List<RelatedResourceRep> unmanaged =
                execute(new GetUnmanagedFilesystemsForStorageSystem(storageSystem));
        if (unmanaged != null) {
            total = unmanaged.size();
        }
        return total;
    }
}
