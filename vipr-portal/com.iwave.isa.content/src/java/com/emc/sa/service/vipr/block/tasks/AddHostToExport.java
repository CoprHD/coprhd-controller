/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportPathParameters;
import com.emc.storageos.model.block.export.ExportUpdateParam;
import com.emc.storageos.model.block.export.HostsUpdateParam;
import com.emc.vipr.client.Task;

public class AddHostToExport extends WaitForTask<ExportGroupRestRep> {

    private final URI exportId;
    private final URI hostId;
    private final Integer minPaths;
    private final Integer maxPaths;
    private final Integer pathsPerInitiator;
    private final URI portGroup;

    public AddHostToExport(URI exportId, URI hostId, Integer minPaths, Integer maxPaths, Integer pathsPerInitiator,URI portGroup) {
        super();
        this.exportId = exportId;
        this.hostId = hostId;
        this.minPaths = minPaths;
        this.maxPaths = maxPaths;
        this.pathsPerInitiator = pathsPerInitiator;
        this.portGroup = portGroup;
        provideDetailArgs(exportId, hostId);
    }

    @Override
    protected Task<ExportGroupRestRep> doExecute() throws Exception {
        ExportUpdateParam exportUpdateParam = new ExportUpdateParam();

        exportUpdateParam.setHosts(new HostsUpdateParam());
        exportUpdateParam.getHosts().getAdd().add(hostId);

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
        return getClient().blockExports().update(exportId, exportUpdateParam);
    }

}
