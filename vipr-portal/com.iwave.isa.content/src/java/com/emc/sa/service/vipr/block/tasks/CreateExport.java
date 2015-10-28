/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.sa.service.vipr.block.ExportVMwareBlockVolumeHelper;
import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.export.ExportCreateParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportPathParameters;
import com.emc.storageos.model.block.export.VolumeParam;
import com.emc.vipr.client.Task;

public class CreateExport extends WaitForTask<ExportGroupRestRep> {
    private final String name;
    private final URI varrayId;
    private final URI projectId;
    private final List<URI> volumeIds;
    private final Integer hlu;
    private final URI hostId;
    private final URI clusterId;
    private final Map<URI, Integer> volumeHlus;
    private final Integer minPaths;
    private final Integer maxPaths;
    private final Integer pathsPerInitiator;

    public CreateExport(String name, URI varrayId, URI projectId, List<URI> volumeIds, Integer hlu, String hostName, URI hostId,
            URI clusterId, Map<URI, Integer> volumeHlus, Integer minPaths, Integer maxPaths, Integer pathsPerInitiator) {
        this.name = name;
        this.varrayId = varrayId;
        this.projectId = projectId;
        this.volumeIds = volumeIds;
        this.hlu = hlu;
        this.hostId = hostId;
        this.clusterId = clusterId;
        this.volumeHlus = volumeHlus;
        this.minPaths = minPaths;
        this.maxPaths = maxPaths;
        this.pathsPerInitiator = pathsPerInitiator;
        if (clusterId != null) {
            provideDetailArgs(name, getMessage("CreateExport.cluster"), hostName, volumeIds, hlu);
        }
        else {
            provideDetailArgs(name, getMessage("CreateExport.hostname"), hostName, volumeIds, hlu);
        }
    }

    @Override
    protected Task<ExportGroupRestRep> doExecute() throws Exception {
        ExportCreateParam export = new ExportCreateParam();
        export.setName(name);
        export.setVarray(varrayId);
        export.setProject(projectId);
        Integer currentHlu = hlu;

        for (URI volumeId : volumeIds) {
            VolumeParam volume = new VolumeParam(volumeId);
            if (currentHlu != null) {
                if (currentHlu.equals(ExportVMwareBlockVolumeHelper.USE_EXISTING_HLU) && volumeHlus != null) {
                    Integer volumeHlu = volumeHlus.get(volume.getId());
                    if (volumeHlu == null) {
                        volume.setLun(-1);
                    } else {
                        volume.setLun(volumeHlu);
                    }
                } else {
                    volume.setLun(currentHlu);
                }
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

        if (minPaths != null && maxPaths != null && pathsPerInitiator != null) {
            ExportPathParameters exportPathParameters = new ExportPathParameters();
            exportPathParameters.setMinPaths(minPaths);
            exportPathParameters.setMaxPaths(maxPaths);
            exportPathParameters.setPathsPerInitiator(pathsPerInitiator);
            export.setExportPathParameters(exportPathParameters);
        }

        return getClient().blockExports().create(export);
    }
}
