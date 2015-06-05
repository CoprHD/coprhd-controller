/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.export.ExportGroupRestRep;

public class FindExportByHostCluster extends ViPRExecutionTask<ExportGroupRestRep> {
    private URI projectId;
    private URI varray;
    private URI host;

    public FindExportByHostCluster(String projectId, String varray, String host) {
        this(uri(projectId), uri(varray), uri(host));
    }

    public FindExportByHostCluster(URI projectId, URI varray, URI host) {
        this.projectId = projectId;
        this.varray = varray;
        this.host = host;
        provideDetailArgs(projectId, varray, host);
    }

    @Override
    public ExportGroupRestRep executeTask() throws Exception {
        List<ExportGroupRestRep> exports = getClient().blockExports().findByHostOrCluster(host, projectId, varray);
        return exports.size() > 0 ? exports.get(0) : null;
    }
}
