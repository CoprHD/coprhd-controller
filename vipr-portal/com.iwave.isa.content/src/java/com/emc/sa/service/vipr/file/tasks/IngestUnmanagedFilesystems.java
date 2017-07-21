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
    private URI targetVarrayId;
    private URI filePolicyId;
    private boolean ingestTargetSystems;

    public IngestUnmanagedFilesystems(String vpoolId, String varrayId, String projectId,
            List<String> unmanagedFilesystemIds) {
        this(uri(vpoolId), uri(varrayId), uri(projectId), uris(unmanagedFilesystemIds));
    }

    public IngestUnmanagedFilesystems(String vpoolId, String varrayId, String projectId,
            List<String> unmanagedFilesystemIds, String targetVarrayId, String policyId, boolean ingestTargetSystems) {
        this(uri(vpoolId), uri(varrayId), uri(projectId), uris(unmanagedFilesystemIds),
                uri(targetVarrayId), uri(policyId), ingestTargetSystems);
    }

    public IngestUnmanagedFilesystems(URI vpoolId, URI varrayId, URI projectId, List<URI> unmanagedFilesystemIds) {
        this.vpoolId = vpoolId;
        this.varrayId = varrayId;
        this.projectId = projectId;
        this.unmanagedFilesystemIds = unmanagedFilesystemIds;
        provideDetailArgs(vpoolId, projectId, varrayId, unmanagedFilesystemIds.size());
    }

    public IngestUnmanagedFilesystems(URI vpoolId, URI varrayId, URI projectId, List<URI> unmanagedFilesystemIds,
            URI targetVarrayId, URI policyId, boolean ingestTargetSystems) {
        this.vpoolId = vpoolId;
        this.varrayId = varrayId;
        this.projectId = projectId;
        this.unmanagedFilesystemIds = unmanagedFilesystemIds;
        this.targetVarrayId = targetVarrayId;
        this.filePolicyId = policyId;
        this.ingestTargetSystems = ingestTargetSystems;
        provideDetailArgs(vpoolId, projectId, varrayId, unmanagedFilesystemIds.size(), targetVarrayId, policyId, ingestTargetSystems);
    }

    @Override
    public List<NamedRelatedResourceRep> executeTask() throws Exception {
        FileSystemIngest ingest = new FileSystemIngest();
        ingest.setProject(projectId);
        ingest.setVarray(varrayId);
        ingest.setVpool(vpoolId);
        if (targetVarrayId != null) {
            ingest.setTargetVarrayId(targetVarrayId);
        }
        if (filePolicyId != null) {
            ingest.setPolicyId(filePolicyId);
        }
        ingest.setIngestTargetSystems(ingestTargetSystems);
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
