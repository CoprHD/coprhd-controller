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
    private final URI portGroup;
    private final URI exportPathPolicy;

    public AddClusterToExport(URI exportId, URI clusterId, Integer minPaths, Integer maxPaths, Integer pathsPerInitiator, 
            URI portGroup, URI exportPathPolicy) {
        super();
        this.exportId = exportId;
        this.clusterId = clusterId;
        this.minPaths = minPaths;
        this.maxPaths = maxPaths;
        this.pathsPerInitiator = pathsPerInitiator;
        this.portGroup = portGroup;
        this.exportPathPolicy = exportPathPolicy;
        provideDetailArgs(exportId, clusterId);
    }

    @Override
    protected Task<ExportGroupRestRep> doExecute() throws Exception {
        ExportUpdateParam exportUpdateParam = new ExportUpdateParam();

        exportUpdateParam.setClusters(new ClustersUpdateParam());
        exportUpdateParam.getClusters().getAdd().add(clusterId);

        // Only add the export path parameters to the call if we have to
        boolean addExportPathParameters = false;
        ExportPathParameters exportPathParameters = new ExportPathParameters();
        if (minPaths != null && maxPaths != null && pathsPerInitiator != null) {
            exportPathParameters.setMinPaths(minPaths);
            exportPathParameters.setMaxPaths(maxPaths);
            exportPathParameters.setPathsPerInitiator(pathsPerInitiator);
            addExportPathParameters = true;
        }
        if (portGroup != null ) {
            exportPathParameters.setPortGroup(portGroup);
            addExportPathParameters = true;
        }
        if (addExportPathParameters) {
            exportUpdateParam.setExportPathParameters(exportPathParameters);
        }
        if (exportPathPolicy != null ) {
            exportUpdateParam.setExportPathPolicy(exportPathPolicy);
        }

        return getClient().blockExports().update(exportId, exportUpdateParam);
    }

}
