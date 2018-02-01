/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import com.emc.sa.service.vipr.block.tasks.IngestUnexportedUnmanagedVolumes;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.file.FileSystemIngest;
import com.google.common.collect.Lists;

public class IngestUnmanagedFilesystems extends ViPRExecutionTask<List<NamedRelatedResourceRep>> {
    private URI vpoolId;
    private URI projectId;
    private URI varrayId;
    private List<URI> unmanagedFilesystemIds;

    public IngestUnmanagedFilesystems(String vpoolId, String varrayId, String projectId,
            List<String> unmanagedFilesystemIds) {
        this(uri(vpoolId), uri(varrayId), uri(projectId), uris(unmanagedFilesystemIds));
    }

    public IngestUnmanagedFilesystems(URI vpoolId, URI varrayId, URI projectId, List<URI> unmanagedFilesystemIds) {
        this.vpoolId = vpoolId;
        this.varrayId = varrayId;
        this.projectId = projectId;
        this.unmanagedFilesystemIds = unmanagedFilesystemIds;
        provideDetailArgs(vpoolId, projectId, varrayId, unmanagedFilesystemIds.size());
    }

    @Override
    public List<NamedRelatedResourceRep> executeTask() throws Exception {
        FileSystemIngest ingest = new FileSystemIngest();
        ingest.setProject(projectId);
        ingest.setVarray(varrayId);
        ingest.setVpool(vpoolId);

        return ingestInChunks(ingest);
    }

    private List<NamedRelatedResourceRep> ingestInChunks(FileSystemIngest ingest) {
        List<NamedRelatedResourceRep> results = Lists.newArrayList();
        int i = 1;
        for (Iterator<URI> ids = unmanagedFilesystemIds.iterator(); ids.hasNext();) {
            i++;
            URI id = ids.next();
            ingest.getUnManagedFileSystems().add(id);
            if (i == IngestUnexportedUnmanagedVolumes.INGEST_CHUNK_SIZE || !ids.hasNext()) {
                results.addAll(ingestFilesystems(ingest));
                ingest.getUnManagedFileSystems().clear();
                i = 0;
            }
        }
        return results;
    }

    protected List<NamedRelatedResourceRep> ingestFilesystems(FileSystemIngest ingest) {
        return getClient().unmanagedFileSystems().ingest(ingest);
    }
}
