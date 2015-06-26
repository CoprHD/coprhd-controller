/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.export.ExportGroupRestRep;

import java.net.URI;
import java.util.List;

public class FindExportsContainingCluster extends ViPRExecutionTask<List<ExportGroupRestRep>> {
    private URI cluster;
    private URI project;
    private URI varray;

    public FindExportsContainingCluster(String cluster, String project, String varrayId) {
        this(uri(cluster), uri(project), uri(varrayId));
    }

    public FindExportsContainingCluster(URI cluster, URI project, URI varrayId) {
        this.cluster =  cluster;
        this.project = project;
        this.varray = varrayId;
        provideDetailArgs(cluster, project, varrayId);
    }

    @Override
    public List<ExportGroupRestRep> executeTask() throws Exception {
        return getClient().blockExports().findByCluster(cluster, project, varray);
    }
}