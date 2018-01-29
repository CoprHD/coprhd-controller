/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.block.export.ExportCreateParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportPathParameters;
import com.emc.storageos.model.block.export.VolumeParam;
import com.emc.vipr.client.Task;

public class CreateExportNoWait extends ViPRExecutionTask<Task<ExportGroupRestRep>> {
    private String name;
    private URI varrayId;
    private URI projectId;
    private List<URI> volumeIds;
    private Integer hlu;
    private URI hostId;
    private URI clusterId;
    private URI portGroup;

    public CreateExportNoWait(String name, URI varrayId, URI projectId, List<URI> volumeIds, Integer hlu, String hostName, URI hostId,
            URI clusterId, URI portGroup) {
        this.name = name;
        this.varrayId = varrayId;
        this.projectId = projectId;
        this.volumeIds = volumeIds;
        this.hlu = hlu;
        this.hostId = hostId;
        this.clusterId = clusterId;
        this.portGroup = portGroup;
        if (clusterId != null) {
            provideDetailArgs(name, getMessage("CreateExport.cluster"), hostName, volumeIds, hlu);
        }
        else {
            provideDetailArgs(name, getMessage("CreateExport.hostname"), hostName, volumeIds, hlu);
        }
    }

    @Override
    public Task<ExportGroupRestRep> executeTask() throws Exception {
        ExportCreateParam export = new ExportCreateParam();
        export.setName(name);
        export.setVarray(varrayId);
        export.setProject(projectId);
        Integer currentHlu = hlu;

        for (URI volumeId : volumeIds) {
            VolumeParam volume = new VolumeParam(volumeId);
            if (currentHlu != null) {
                volume.setLun(currentHlu);
            }
            if ((currentHlu != null) && (currentHlu > -1)) {
                currentHlu++;
            }
            export.getVolumes().add(volume);
        }

        if (clusterId != null) {
            export.addCluster(clusterId);
            export.setType("Cluster");
        }
        else {
            export.addHost(hostId);
            export.setType("Host");
        }
        
        if (!NullColumnValueGetter.isNullURI(portGroup)) {
            ExportPathParameters exportPathParameters = new ExportPathParameters();
            exportPathParameters.setPortGroup(portGroup);
            export.setExportPathParameters(exportPathParameters);
        }
        Task<ExportGroupRestRep> task = getClient().blockExports().create(export);
        addOrderIdTag(task.getTaskResource().getId());
        return task;
    }
}
