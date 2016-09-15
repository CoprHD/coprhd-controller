/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.export.ClustersUpdateParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportPathParameters;
import com.emc.storageos.model.block.export.ExportUpdateParam;
import com.emc.vipr.client.Task;

public class AddClusterToExport extends WaitForTask<ExportGroupRestRep> {

    private final URI exportId;
    private final URI clusterId;
    private final Integer minPaths;
    private final Integer maxPaths;
    private final Integer pathsPerInitiator;

    public AddClusterToExport(URI exportId, URI clusterId, Integer minPaths, Integer maxPaths, Integer pathsPerInitiator) {
        super();
        this.exportId = exportId;
        this.clusterId = clusterId;
        this.minPaths = minPaths;
        this.maxPaths = maxPaths;
        this.pathsPerInitiator = pathsPerInitiator;
        provideDetailArgs(exportId, clusterId);
    }

    @Override
    protected Task<ExportGroupRestRep> doExecute() throws Exception {
        ExportUpdateParam exportUpdateParam = new ExportUpdateParam();

        exportUpdateParam.setClusters(new ClustersUpdateParam());
        exportUpdateParam.getClusters().getAdd().add(clusterId);

        if (minPaths != null && maxPaths != null && pathsPerInitiator != null) {
            ExportPathParameters exportPathParameters = new ExportPathParameters();
            exportPathParameters.setMinPaths(minPaths);
            exportPathParameters.setMaxPaths(maxPaths);
            exportPathParameters.setPathsPerInitiator(pathsPerInitiator);
            exportUpdateParam.setExportPathParameters(exportPathParameters);
        }

        return getClient().blockExports().update(exportId, exportUpdateParam);
    }

}
