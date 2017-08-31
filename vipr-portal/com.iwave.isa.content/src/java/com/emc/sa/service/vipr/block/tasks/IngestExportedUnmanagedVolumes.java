/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.UnManagedVolumeRestRep;
import com.emc.storageos.model.block.VolumeExportIngestParam;
import com.emc.vipr.client.Tasks;

/**
 * @author Chris Dail
 */
public class IngestExportedUnmanagedVolumes extends WaitForTasks<UnManagedVolumeRestRep> {

    public static final int MAX_ERROR_DISPLAY = 10;

    private URI vpoolId;
    private URI projectId;
    private URI varrayId;
    private URI hostId;
    private URI clusterId;
    private List<URI> unmanagedVolumeIds;
    private String ingestionMethod;

    public IngestExportedUnmanagedVolumes(URI vpoolId, URI varrayId, URI projectId, URI hostId, 
            URI clusterId, List<URI> unmanagedVolumeIds, String ingestionMethod) {
        this.vpoolId = vpoolId;
        this.varrayId = varrayId;
        this.projectId = projectId;
        this.hostId = hostId;
        this.clusterId = clusterId;
        this.unmanagedVolumeIds = unmanagedVolumeIds;
        this.ingestionMethod = ingestionMethod;
        setWaitFor(true);
        setMaxErrorDisplay(MAX_ERROR_DISPLAY);
        provideDetailArgs(vpoolId, projectId, varrayId, hostId != null ? hostId : clusterId, unmanagedVolumeIds.size());
    }

    protected Tasks<UnManagedVolumeRestRep> ingestVolumes(VolumeExportIngestParam ingest) {
        return getClient().unmanagedVolumes().ingestExported(ingest);
    }

    @Override
    protected Tasks<UnManagedVolumeRestRep> doExecute() throws Exception {
        VolumeExportIngestParam ingest = new VolumeExportIngestParam();
        ingest.setVpool(vpoolId);
        ingest.setProject(projectId);
        ingest.setVarray(varrayId);
        ingest.setCluster(clusterId);
        ingest.setHost(hostId);
        ingest.setVplexIngestionMethod(ingestionMethod);
        ingest.getUnManagedVolumes().addAll(unmanagedVolumeIds);

        Tasks<UnManagedVolumeRestRep> results = new Tasks<UnManagedVolumeRestRep>(getClient().auth().getClient(), null,
                UnManagedVolumeRestRep.class);
        Tasks<UnManagedVolumeRestRep> tasks = ingestVolumes(ingest);
        results.getTasks().addAll(tasks.getTasks());

        return results;
    }
}
