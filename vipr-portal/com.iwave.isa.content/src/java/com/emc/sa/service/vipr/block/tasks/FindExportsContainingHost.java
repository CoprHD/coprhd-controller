/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.export.ExportGroupRestRep;

import java.net.URI;
import java.util.List;

public class FindExportsContainingHost extends ViPRExecutionTask<List<ExportGroupRestRep>> {
    private URI host;
    private URI project;
    private URI varray;

    public FindExportsContainingHost(String host, String project, String varrayId) {
        this(uri(host), uri(project), uri(varrayId));
    }

    public FindExportsContainingHost(URI host, URI project, URI varrayId) {
        this.host =  host;
        this.project = project;
        this.varray = varrayId;
        provideDetailArgs(host, project, varrayId);
    }

    @Override
    public List<ExportGroupRestRep> executeTask() throws Exception {
        return getClient().blockExports().findContainingHost(host, project, varray);
    }
}
