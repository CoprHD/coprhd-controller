/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.INGESTION_METHOD;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.GetUnmanagedVolumesByHostOrCluster;
import com.emc.sa.service.vipr.block.tasks.IngestExportedUnmanagedVolumes;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.block.IngestionMethodEnum;

@Service("IngestExportedUnmanagedVolumes")
public class IngestExportedUnmanagedVolumesService extends ViPRService {
    @Param(HOST)
    protected URI hostId;

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

    private Host host;
    private Cluster cluster;

    @Override
    public void precheck() {
        if (BlockStorageUtils.isHost(hostId)) {
            host = BlockStorageUtils.getHost(hostId);
        }
        else {
            cluster = BlockStorageUtils.getCluster(hostId);
        }
    }

    @Override
    public void execute() throws Exception {
        if (ingestionMethod == null || ingestionMethod.isEmpty()) {
            ingestionMethod = IngestionMethodEnum.FULL.toString();
        }
        
        int succeed = execute(new IngestExportedUnmanagedVolumes(virtualPool, virtualArray, project,
                host == null ? null : host.getId(),
                cluster == null ? null : cluster.getId(),
                uris(volumeIds),
                ingestionMethod
                )).getTasks().size();
        logInfo("ingest.exported.unmanaged.volume.service.ingested", succeed);
        logInfo("ingest.exported.unmanaged.volume.service.skipped", volumeIds.size() - succeed);

        int remaining = execute(new GetUnmanagedVolumesByHostOrCluster(
                host != null ? host.getId() : cluster.getId())).size();

        logInfo("ingest.exported.unmanaged.volume.service.remaining", remaining);
    }
}
