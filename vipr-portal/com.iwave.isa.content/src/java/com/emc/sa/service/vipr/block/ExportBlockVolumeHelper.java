/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.HLU;
import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.MAX_PATHS;
import static com.emc.sa.service.ServiceParams.MIN_PATHS;
import static com.emc.sa.service.ServiceParams.PATHS_PER_INITIATOR;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SNAPSHOTS;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VOLUME;
import static com.emc.sa.service.ServiceParams.VOLUMES;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;
import static com.emc.sa.service.vipr.ViPRService.uris;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 */
public class ExportBlockVolumeHelper {
    @Param(HOST)
    protected URI hostId;

    @Param(value = VIRTUAL_ARRAY, required = false)
    protected URI virtualArrayId;

    @Param(PROJECT)
    protected URI projectId;

    @Param(value = VOLUMES, required = false)
    protected List<String> volumeIds;

    @Param(value = VOLUME, required = false)
    protected String volumeId;

    @Param(value = SNAPSHOTS, required = false)
    protected List<String> snapshotIds;

    @Param(value = HLU, required = false)
    protected Integer hlu;

    @Param(value = MIN_PATHS, required = false)
    protected Integer minPaths;

    @Param(value = MAX_PATHS, required = false)
    protected Integer maxPaths;

    @Param(value = PATHS_PER_INITIATOR, required = false)
    protected Integer pathsPerInitiator;

    protected Host host;
    protected Cluster cluster;

    public void precheck() {
        if (hlu == null) {
            hlu = -1;
        }

        if (volumeId == null && volumeIds == null && snapshotIds == null) {
            ExecutionUtils.fail("failTask.ExportBlockVolumeHelper.precheck", new Object[] {}, new Object[] { VOLUME, VOLUMES, SNAPSHOTS });
        }

        precheckExportPathParameters(minPaths, maxPaths, pathsPerInitiator);

        if (volumeIds == null || volumeIds.isEmpty() && volumeId != null) {
            volumeIds = Collections.singletonList(volumeId);
        }

        if (BlockStorageUtils.isHost(hostId)) {
            host = BlockStorageUtils.getHost(hostId);
        }
        else {
            cluster = BlockStorageUtils.getCluster(hostId);
        }
    }

    /** convenience method for exporting volumes */
    public List<ExportGroupRestRep> exportVolumes() {
        return exportBlockResources(uris(volumeIds));
    }

    /**
     * export the block resources identified by URIs in the given resource id list
     * 
     * @param resourceIds the list of URIs which identify the block resources that need to be exported
     * @return The list of export groups which have been created/updated
     */
    public List<ExportGroupRestRep> exportBlockResources(List<URI> resourceIds) {
        // the list of exports to return
        List<ExportGroupRestRep> exports = Lists.newArrayList();

        List<URI> newVolumes = new ArrayList<URI>();
        Map<URI, Set<URI>> addVolumeExports = Maps.newHashMap();

        // we will need to keep track of the current HLU number
        Integer currentHlu = hlu;

        // get a list of all block resources using the id list provided
        List<BlockObjectRestRep> blockResources = BlockStorageUtils.getBlockResources(resourceIds);
        URI virtualArrayId = null;

        for (BlockObjectRestRep blockResource : blockResources) {
            virtualArrayId = getVirtualArrayId(blockResource);

            // see if we can find an export that uses this block resource
            ExportGroupRestRep export = findExistingExportGroup(blockResource, virtualArrayId);

            // If the export does not exist for this volume
            if (export == null) {
                newVolumes.add(blockResource.getId());
            }
            // Export exists, check if volume belongs to it
            else {
                if (BlockStorageUtils.isVolumeInExportGroup(export, blockResource.getId())) {
                    logInfo("export.block.volume.contains.volume", export.getId(), blockResource.getId());
                }
                else {
                    updateExportVolumes(export, blockResource, addVolumeExports);
                }
                exports.add(export);
            }
        }

        // Bulk update multiple volumes to single export
        List<URI> volumeIds = Lists.newArrayList();
        for (Map.Entry<URI, Set<URI>> entry : addVolumeExports.entrySet()) {
            volumeIds.addAll(entry.getValue());
        }

        Map<URI, Integer> volumeHlus = getVolumeHLUs(volumeIds);

        for (Map.Entry<URI, Set<URI>> entry : addVolumeExports.entrySet()) {
            BlockStorageUtils.addVolumesToExport(entry.getValue(), currentHlu, entry.getKey(), volumeHlus, minPaths, maxPaths,
                    pathsPerInitiator);
            logInfo("export.block.volume.add.existing", entry.getValue(), entry.getKey());
            if ((currentHlu != null) && (currentHlu > -1)) {
                currentHlu += entry.getValue().size();
            }
        }

        // Create new export with multiple volumes that don't belong to an export
        if (!newVolumes.isEmpty()) {
            volumeHlus = getVolumeHLUs(newVolumes);
            URI exportId = null;
            if (cluster != null) {
                exportId = BlockStorageUtils.createClusterExport(projectId, virtualArrayId, newVolumes, currentHlu, cluster, volumeHlus,
                        minPaths, maxPaths, pathsPerInitiator);
            } else {
                exportId = BlockStorageUtils.createHostExport(projectId, virtualArrayId, newVolumes, currentHlu, host, volumeHlus,
                        minPaths, maxPaths, pathsPerInitiator);
            }
            ExportGroupRestRep export = BlockStorageUtils.getExport(exportId);

            // add this export to the list of exports we will return to the caller
            exports.add(export);
        }
        // add host or cluster to the affected resources
        if (host != null) {
            ExecutionUtils.addAffectedResource(host.getId().toString());
        } else if (cluster != null) {
            ExecutionUtils.addAffectedResource(cluster.getId().toString());
        }

        // Clear the rollback at this point so later errors won't undo the exports
        ExecutionUtils.clearRollback();
        return exports;
    }

    private void updateExportVolumes(ExportGroupRestRep export, BlockObjectRestRep volume, Map<URI, Set<URI>> addVolumeExports) {
        // Store mapping of export to volumes that will be bulk updated
        Set<URI> value = addVolumeExports.get(export.getId());
        if (value == null) {
            value = new HashSet<URI>();
            value.add(volume.getId());
        } else {
            value.add(volume.getId());
        }
        addVolumeExports.put(export.getId(), value);
    }

    private URI getVirtualArrayId(BlockObjectRestRep blockResource) {
        // if we got a VArray from the form then we can just return that
        if (virtualArrayId != null) {
            return virtualArrayId;
        }
        else {
            return BlockStorageUtils.getVirtualArrayId(blockResource);
        }
    }

    private ExportGroupRestRep findExistingExportGroup(BlockObjectRestRep volume, URI virtualArrayId) {
        if (cluster != null) {
            return BlockStorageUtils.findExportByCluster(cluster, projectId, virtualArrayId, volume.getId());
        }

        // Attempt to find one (regardless of type) that contains the Volume
        List<ExportGroupRestRep> exportGroups = BlockStorageUtils.findExportsContainingHost(host.getId(), projectId, virtualArrayId);
        for (ExportGroupRestRep exportGroup : exportGroups) {
            if (BlockStorageUtils.isVolumeInExportGroup(exportGroup, volume.getId())) {
                return exportGroup;
            }
        }

        // Didn't find it, so find a Host or Exclusive Type
        for (ExportGroupRestRep exportGroup : exportGroups) {
            if (!exportGroup.getType().equals("Cluster")) {
                return exportGroup;
            }
        }

        return null;
    }

    public URI getHostId() {
        return hostId;
    }

    public List<String> getVolumeIds() {
        return volumeIds;
    }

    protected Map<URI, Integer> getVolumeHLUs(List<URI> volumeIds) {
        // only ExportVMwareBlockVolumeHelper supports setting HLUs for now
        return Maps.newHashMap();
    }

    public static void precheckExportPathParameters(Integer minPaths, Integer maxPaths, Integer pathsPerInitiator) {
        if (minPaths != null || maxPaths != null || pathsPerInitiator != null) {
            if ((minPaths == null || maxPaths == null || pathsPerInitiator == null) || minPaths < 1 || maxPaths < 1
                    || pathsPerInitiator < 1) {
                ExecutionUtils.fail("failTask.exportPathParameters.precheck", new Object[] {}, new Object[] {});
            } else if (minPaths > maxPaths) {
                ExecutionUtils.fail("failTask.exportPathParameters.minPathsCheck", new Object[] {}, new Object[] {});
            } else if (pathsPerInitiator > maxPaths) {
                ExecutionUtils.fail("failTask.exportPathParameters.pathsPerInitiator", new Object[] {}, new Object[] {});
            }
        }
    }
}
