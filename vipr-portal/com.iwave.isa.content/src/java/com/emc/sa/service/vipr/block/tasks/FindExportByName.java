/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.export.ExportGroupRestRep;

public class FindExportByName extends ViPRExecutionTask<ExportGroupRestRep> {
    private final String name;
    private final URI project;
    private final URI varray;

    public FindExportByName(String name, String project, String varrayId) {
        this(name, uri(project), uri(varrayId));
    }

    public FindExportByName(String name, URI project, URI varrayId) {
        this.name = name;
        this.project = project;
        this.varray = varrayId;
        provideDetailArgs(name, project, varrayId);
    }

    @Override
    public ExportGroupRestRep executeTask() throws Exception {
        List<ExportGroupRestRep> exports = getClient().blockExports().findByName(name, project, varray);
        for (ExportGroupRestRep export : exports) {
            return export;
        }
        return null;
    }

}
