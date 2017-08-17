/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.UnManagedVolumeRestRep;
import com.emc.storageos.model.block.VolumeIngest;
import com.emc.vipr.client.Tasks;

/**
 * @author Chris Dail
 */
public class IngestUnexportedUnmanagedVolumes extends WaitForTasks<UnManagedVolumeRestRep> {

    public static final int INGEST_CHUNK_SIZE = 1000;
    public static final int MAX_ERROR_DISPLAY = 10;

    private URI vpoolId;
    private URI projectId;
    private URI varrayId;
    private List<URI> unmanagedVolumeIds;
    private String ingestionMethod;

    public IngestUnexportedUnmanagedVolumes(String vpoolId, String varrayId, String projectId, List<String> unmanagedVolumeIds, String ingestionMethod) {
        this(uri(vpoolId), uri(varrayId), uri(projectId), uris(unmanagedVolumeIds), ingestionMethod);
    }

    public IngestUnexportedUnmanagedVolumes(URI vpoolId, URI varrayId, URI projectId, List<URI> unmanagedVolumeIds, String ingestionMethod) {
        this.vpoolId = vpoolId;
        this.varrayId = varrayId;
        this.projectId = projectId;
        this.unmanagedVolumeIds = unmanagedVolumeIds;
        this.ingestionMethod = ingestionMethod;
        setWaitFor(true);
        setMaxErrorDisplay(MAX_ERROR_DISPLAY);
        provideDetailArgs(vpoolId, projectId, varrayId, unmanagedVolumeIds.size());
    }

    @Override
    protected Tasks<UnManagedVolumeRestRep> doExecute() throws Exception {

        VolumeIngest ingest = new VolumeIngest();
        ingest.setVpool(vpoolId);
        ingest.setProject(projectId);
        ingest.setVarray(varrayId);
        ingest.setVplexIngestionMethod(ingestionMethod);
        ingest.getUnManagedVolumes().addAll(unmanagedVolumeIds);

        Tasks<UnManagedVolumeRestRep> results = 
                new Tasks<UnManagedVolumeRestRep>(getClient().auth().getClient(), null,
                UnManagedVolumeRestRep.class);
        Tasks<UnManagedVolumeRestRep> tasks = ingestVolumes(ingest);
        results.getTasks().addAll(tasks.getTasks());

        return results;
    }

    private Tasks<UnManagedVolumeRestRep> ingestVolumes(VolumeIngest ingest) {
        return getClient().unmanagedVolumes().ingest(ingest);
    }
}
