/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.block.ExportVMwareBlockVolumeHelper;
import com.emc.sa.service.vipr.compute.ComputeUtils;
import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.api.mapper.HostMapper;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.export.ExportCreateParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportPathParameters;
import com.emc.storageos.model.block.export.VolumeParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.vipr.client.Task;
import com.google.common.collect.Lists;

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
    private final URI portGroup;
    private static final String VMAX = "vmax";

    public CreateExport(String name, URI varrayId, URI projectId, List<URI> volumeIds, Integer hlu, String hostName, URI hostId,
            URI clusterId, Map<URI, Integer> volumeHlus, Integer minPaths, Integer maxPaths, Integer pathsPerInitiator, URI portGroup) {
        this.name = name;
        this.varrayId = varrayId;
        this.projectId = projectId;
        this.volumeIds = volumeIds;
        this.hlu = (hlu == null ? -1 : hlu);
        this.hostId = hostId;
        this.clusterId = clusterId;
        this.volumeHlus = volumeHlus;
        this.minPaths = minPaths;
        this.maxPaths = maxPaths;
        this.pathsPerInitiator = pathsPerInitiator;
        this.portGroup = portGroup;
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

        setVolumeLun(export);

        if (clusterId != null) {
            export.addCluster(clusterId);
            export.setType("Cluster");
        }
        else {
            export.addHost(hostId);
            export.setType("Host");
        }
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
            export.setExportPathParameters(exportPathParameters);
        }

        return getClient().blockExports().create(export);
    }

    private void setVolumeLun(ExportCreateParam export) {
        Integer currentHlu = (hlu == null) ? -1 : hlu;
        // check if the volume is for HPUX host and using a VMAX volume the HLU needs to be set differently
        boolean isHpuxExportOnVmax = isExportForHpuxOnVmax();
        if (currentHlu != -1 && isHpuxExportOnVmax && !BlockStorageUtils.isValidHpuxHlu(Integer.toHexString(currentHlu))) {
            throw stateException("CreateExport.iilegalState.invalidHLU", currentHlu);
        }
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
                do {
                    currentHlu++;
                } while (isHpuxExportOnVmax && !BlockStorageUtils.isValidHpuxHlu(Integer.toHexString(currentHlu)));
            }
            export.getVolumes().add(volume);
        }

    }

    private boolean isExportForHpuxOnVmax() {
        // get host list
        List<HostRestRep> hosts = new ArrayList<HostRestRep>();
        if (clusterId != null) {
            hosts = ComputeUtils.getHostsInCluster(clusterId);
        } else {
            Host host = BlockStorageUtils.getHost(hostId);
            HostRestRep hostRestRep = HostMapper.map(host);
            hosts = Lists.newArrayList(hostRestRep);
        }
        // find if its a HPUX host
        boolean hasHPUX = false;
        for (HostRestRep host : hosts) {
            if (host.getType() != null &&
                    (host.getType().equalsIgnoreCase(Host.HostType.HPUX.toString()))) {
                hasHPUX = true;
                break;
            }
        }
        if (!hasHPUX) {
            return false;
        }

        // now if we are to continue then we are having an HPUX host. So we will check the volume storage system for
        // VMAX
        for (URI volumeId : volumeIds) {
            BlockObjectRestRep volume = BlockStorageUtils.getVolume(volumeId);
            URI storageURI = volume.getStorageController();
            StorageSystemRestRep storageSystem = BlockStorageUtils.getStorageSystem(storageURI);
            if (StringUtils.equals(VMAX, storageSystem.getSystemType())) {
                return true;
            }

        }
        return false;
    }
}
