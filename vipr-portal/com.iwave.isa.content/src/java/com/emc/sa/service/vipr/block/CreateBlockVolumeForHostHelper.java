package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.HLU;
import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.MAX_PATHS;
import static com.emc.sa.service.ServiceParams.MIN_PATHS;
import static com.emc.sa.service.ServiceParams.PATHS_PER_INITIATOR;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.google.common.collect.Lists;

public class CreateBlockVolumeForHostHelper extends CreateBlockVolumeHelper {

    public static final int EXPORT_CHUNK_SIZE = 100;

    @Param(HOST)
    protected URI hostId;

    @Param(value = HLU, required = false)
    protected Integer hlu;

    @Param(value = MIN_PATHS, required = false)
    protected Integer minPaths;

    @Param(value = MAX_PATHS, required = false)
    protected Integer maxPaths;

    @Param(value = PATHS_PER_INITIATOR, required = false)
    protected Integer pathsPerInitiator;

    private Host host;
    private Cluster cluster;

    @Override
    public URI getComputeResource() {
        URI computeResource = null;
        if (BlockStorageUtils.isHost(hostId)) {
            host = BlockStorageUtils.getHost(hostId);
            computeResource = host.getId();
        }
        else {
            cluster = BlockStorageUtils.getCluster(hostId);
            computeResource = cluster.getId();
        }
        return computeResource;
    }

    public void precheck() {

        ExportBlockVolumeHelper.precheckExportPathParameters(minPaths, maxPaths, pathsPerInitiator);

        if (BlockStorageUtils.isHost(hostId)) {
            host = BlockStorageUtils.getHost(hostId);
        }
        else {
            cluster = BlockStorageUtils.getCluster(hostId);
        }

        BlockStorageUtils.checkEvents(host != null ? host : cluster);

    }

    public List<BlockObjectRestRep> exportVolumes(List<URI> volumeIds) {
        List<URI> batchVolumeIds = Lists.newArrayList();
        int batchCount = 0;
        Iterator<URI> ids = volumeIds.iterator();
        while (ids.hasNext()) {
            batchCount++;
            URI id = ids.next();
            batchVolumeIds.add(id);
            if (batchCount == EXPORT_CHUNK_SIZE || !ids.hasNext()) {
                // See if an existing export exists for the host ports
                ExportGroupRestRep export = null;
                if (cluster != null) {
                    export = BlockStorageUtils.findExportByCluster(cluster, project, virtualArray, null);
                } else {
                    export = BlockStorageUtils.findExportByHost(host, project, virtualArray, null);
                }
                // If did not find export group for the host/cluster, try find existing empty export with
                // host/cluster name
                boolean createExport = export == null;
                boolean isEmptyExport = export != null && BlockStorageUtils.isEmptyExport(export);
                String exportName = cluster != null ? cluster.getLabel() : host.getHostName();
                if (export == null) {
                    export = BlockStorageUtils.findExportsByName(exportName, project, virtualArray);
                    isEmptyExport = export != null && BlockStorageUtils.isEmptyExport(export);
                    createExport = export == null || !isEmptyExport;
                    // If there is an existing non-empty export with the same name, append a time stamp to the name to make it unique
                    if (export != null && !isEmptyExport) {
                        exportName = exportName + BlockStorageUtils.UNDERSCORE
                                + new SimpleDateFormat("yyyyMMddhhmmssSSS").format(new Date());
                    }
                }

                // If the export does not exist or there is a non-empty export with the same name, create a new one
                if (createExport) {
                    URI exportId = null;
                    if (cluster != null) {
                        exportId = BlockStorageUtils.createClusterExport(exportName, project, virtualArray, batchVolumeIds, hlu, cluster,
                                new HashMap<URI, Integer>(), minPaths, maxPaths, pathsPerInitiator);
                    } else {
                        exportId = BlockStorageUtils.createHostExport(exportName, project, virtualArray, batchVolumeIds, hlu, host,
                                new HashMap<URI, Integer>(),
                                minPaths, maxPaths, pathsPerInitiator);
                    }
                    logInfo("create.block.volume.create.export", exportId);
                }
                // Add the volume to the existing export
                else {
                    BlockStorageUtils.addVolumesToExport(batchVolumeIds, hlu, export.getId(), new HashMap<URI, Integer>(), minPaths, maxPaths,
                            pathsPerInitiator);
                    // Since the existing export can also be an empty export, also check if the host/cluster is present in the export.
                    // If not, add them.
                    if (isEmptyExport) {
                        if (cluster != null) {
                            BlockStorageUtils.addClusterToExport(export.getId(), cluster.getId(), minPaths, maxPaths, pathsPerInitiator);
                        } else {
                            BlockStorageUtils.addHostToExport(export.getId(), host.getId(), minPaths, maxPaths, pathsPerInitiator);
                        }
                    }
                    logInfo("create.block.volume.update.export", export.getId());
                }

                batchVolumeIds.clear();
                batchCount = 0;
            }
        }

        if (host != null) {
            ExecutionUtils.addAffectedResource(host.getId().toString());
        } else if (cluster != null) {
            ExecutionUtils.addAffectedResource(cluster.getId().toString());
        }

        // The volume is created and exported, clear the rollback steps so it will still be available if any other
        // further steps fail
        ExecutionUtils.clearRollback();

        // Get the volumes after exporting, volumes would not have WWNs until after export in VPLEX
        List<BlockObjectRestRep> volumes = BlockStorageUtils.getBlockResources(volumeIds);
        return volumes;
    }


    public List<BlockObjectRestRep> createAndExportVolumes() {
        List<URI> volumeIds = createVolumes(getComputeResource());
        return exportVolumes(volumeIds);
    }
}
