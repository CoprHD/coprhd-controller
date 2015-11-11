package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.HLU;
import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.MAX_PATHS;
import static com.emc.sa.service.ServiceParams.MIN_PATHS;
import static com.emc.sa.service.ServiceParams.PATHS_PER_INITIATOR;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;

public class CreateBlockVolumeForHostHelper extends CreateBlockVolumeHelper {
    
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

    public void precheck() {

        ExportBlockVolumeHelper.precheckExportPathParameters(minPaths, maxPaths, pathsPerInitiator);

        if (BlockStorageUtils.isHost(hostId)) {
            host = BlockStorageUtils.getHost(hostId);
        }
        else {
            cluster = BlockStorageUtils.getCluster(hostId);
        }
    }

    public List<BlockObjectRestRep> exportVolumes(List<URI> volumeIds) {
        // See if an existing export exists for the host ports
        ExportGroupRestRep export = null;
        if (cluster != null) {
            export = BlockStorageUtils.findExportByCluster(cluster, project, virtualArray, null);
        } else {
            export = BlockStorageUtils.findExportByHost(host, project, virtualArray, null);
        }

        // If the export does not exist, create it
        if (export == null) {
            URI exportId = null;
            if (cluster != null) {
                exportId = BlockStorageUtils.createClusterExport(project, virtualArray, volumeIds, hlu, cluster,
                        new HashMap<URI, Integer>(), minPaths, maxPaths, pathsPerInitiator);
            } else {
                exportId = BlockStorageUtils.createHostExport(project, virtualArray, volumeIds, hlu, host, new HashMap<URI, Integer>(),
                        minPaths, maxPaths, pathsPerInitiator);
            }
            logInfo("create.block.volume.create.export", exportId);
        }
        // Add the volume to the existing export
        else {
            BlockStorageUtils.addVolumesToExport(volumeIds, hlu, export.getId(), new HashMap<URI, Integer>(), minPaths, maxPaths,
                    pathsPerInitiator);
            logInfo("create.block.volume.update.export", export.getId());
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
        List<URI> volumeIds = createVolumes();
        return exportVolumes(volumeIds);
    }
}
