/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.INGESTION_METHOD;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.STORAGE_SYSTEMS;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.GetUnmanagedVolumes;
import com.emc.sa.service.vipr.block.tasks.IngestUnexportedUnmanagedVolumes;
import com.emc.sa.service.vipr.tasks.CheckStorageSystemDiscoveryStatus;
import com.emc.storageos.model.block.UnManagedVolumeRestRep;

@Service("IngestUnexportedUnmanagedVolumes")
public class IngestUnexportedUnmanagedVolumesService extends ViPRService {
    @Param(STORAGE_SYSTEMS)
    protected URI storageSystem;

    @Param(VIRTUAL_POOL)
    protected URI virtualPool;

    @Param(PROJECT)
    protected URI project;

    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;
    
    @Param(value = INGESTION_METHOD, required = false)
    protected String ingestionMethod;

    @Param(VOLUMES)
    protected List<String> volumeIds;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        execute(new CheckStorageSystemDiscoveryStatus(storageSystem));
    }

    @Override
    public void execute() throws Exception {
        List<UnManagedVolumeRestRep> unmanagedVolumes = execute(new GetUnmanagedVolumes(storageSystem, virtualPool));

        execute(new IngestUnexportedUnmanagedVolumes(virtualPool, virtualArray, project, uris(volumeIds)));

        // Requery and produce a log of what was ingested or not
        int failed = execute(new GetUnmanagedVolumes(storageSystem, virtualPool)).size();
        logInfo("ingest.unexported.unmanaged.volume.service.ingested", unmanagedVolumes.size() - failed);
        logInfo("ingest.unexported.unmanaged.volume.service.skipped", failed);
    }
}
