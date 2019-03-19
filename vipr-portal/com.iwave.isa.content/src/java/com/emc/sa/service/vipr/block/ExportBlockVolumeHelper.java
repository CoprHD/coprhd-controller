/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.COPIES;
import static com.emc.sa.service.ServiceParams.HLU;
import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.MAX_PATHS;
import static com.emc.sa.service.ServiceParams.MIN_PATHS;
import static com.emc.sa.service.ServiceParams.PATHS_PER_INITIATOR;
import static com.emc.sa.service.ServiceParams.PORT_GROUP;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SNAPSHOTS;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VOLUME;
import static com.emc.sa.service.ServiceParams.VOLUMES;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;
import static com.emc.sa.service.vipr.ViPRService.uris;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;
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

    @Param(value = COPIES, required = false)
    protected List<String> copiesIds;

    @Param(value = HLU, required = false)
    protected Integer hlu;

    @Param(value = MIN_PATHS, required = false)
    protected Integer minPaths;

    @Param(value = MAX_PATHS, required = false)
    protected Integer maxPaths;

    @Param(value = PATHS_PER_INITIATOR, required = false)
    protected Integer pathsPerInitiator;

    @Param(value = PORT_GROUP, required = false)
    protected URI portGroup;

    protected Host host;
    protected Cluster cluster;

    public void precheck() {
        // check to select max 100 resources at a time.
        ViPRService.checkForMaxResouceOrderLimit(volumeIds);
        if (hlu == null) {
            hlu = -1;
        }

        if (volumeId == null && volumeIds == null && snapshotIds == null && copiesIds == null) {
            ExecutionUtils.fail("failTask.ExportBlockVolumeHelper.precheck", new Object[] {}, new Object[] { VOLUME, VOLUMES, SNAPSHOTS, COPIES });
        }

        precheckExportPathParameters(minPaths, maxPaths, pathsPerInitiator);

        if (volumeIds == null || volumeIds.isEmpty() && volumeId != null) {
            volumeIds = Collections.singletonList(volumeId);
        }
        precheckPortGroupParameter(ResourceUtils.uri(volumeIds.get(0)), hostId, projectId, virtualArrayId, portGroup);
        
        if (BlockStorageUtils.isHost(hostId)) {
            host = BlockStorageUtils.getHost(hostId);
        }
        else {
            cluster = BlockStorageUtils.getCluster(hostId);
        }

        // Don't allow ViPR exports of block volumes (this may not fly as part of the create host services)
        ViPRService.checkForBootVolumes(volumeIds);
        
        BlockStorageUtils.checkEvents(host != null ? host : cluster);
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
        return exportBlockResources(resourceIds, null);
    }

    /**
     * export the block resources identified by URIs in the given resource id list
     *
     * @param resourceIds the list of URIs which identify the block resources that need to be exported
     * @param parentId the parent URI for the list of resourceIds
     * @return The list of export groups which have been created/updated
     */
    public List<ExportGroupRestRep> exportBlockResources(List<URI> resourceIds, URI parentId) {
        // the list of exports to return
        List<ExportGroupRestRep> exports = Lists.newArrayList();

        List<URI> newVolumes = new ArrayList<URI>();
        Map<URI, Set<URI>> addVolumeExports = Maps.newHashMap();
        Map<URI, URI> addComputeResourceToExports = Maps.newHashMap();

        // we will need to keep track of the current HLU number
        Integer currentHlu = hlu;

        // get a list of all block resources using the id list provided
        List<BlockObjectRestRep> blockResources = BlockStorageUtils.getBlockResources(resourceIds, parentId);
        URI virtualArrayId = null;
        String exportName = cluster != null ? cluster.getLabel() : host.getHostName();
        // Flag to indicate an empty ExportGroup object in ViPR, i.e., ExportGroup without any volumes and initiators in it.
        boolean isEmptyExport = true;
        ExportGroupRestRep export = null;
        // For every block object, 
        // 1) check if there are existing ExportGroup object corresponding to the host/cluster.
        //    If yes, add the block object to the ExportGroup.
        // 2) If there are no existing ViPR ExportGroup for the host/cluster, check if there is an 
        //    ExportGroup object with name matching the host/cluster name.
        //	  a) If the ExportGroup object is empty, add the block object and host/cluster to the ExportGroup.
        //    b) If the ExportGroup object is not empty, then create a new ExportGroup by appending a time stamp
        //       to the host/cluster name
        // 3) If there is no ExportGroup found, create a new ExportGroup.        
        
        for (BlockObjectRestRep blockResource : blockResources) {
            virtualArrayId = getVirtualArrayId(blockResource);
            // see if we can find an export that uses this block resource
            export = findExistingExportGroup(blockResource, virtualArrayId);
            boolean createExport = export == null;
            isEmptyExport = export != null && BlockStorageUtils.isEmptyExport(export);
            // If did not find export group for the host/cluster, try find existing empty export with
            // host/cluster name
            if (export == null) {
                export = BlockStorageUtils.findExportsByName(exportName, projectId, virtualArrayId);
                isEmptyExport = export != null && BlockStorageUtils.isEmptyExport(export);
                createExport = export == null || !isEmptyExport;
            }
            if (createExport) {
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

                // Since the existing export can also be an empty export, also check if the host/cluster is present in the export.
                // If not, add them.
                if (isEmptyExport) {
                    URI computeResource = cluster != null ? cluster.getId() : host.getId();
                    addComputeResourceToExports.put(export.getId(), computeResource);
                }

                exports.add(export);
            }
        }
        // If there is an existing non-empty export with the same name, append a time stamp to the name to make it unique
        if (export != null && !isEmptyExport) {
            exportName = exportName + BlockStorageUtils.UNDERSCORE + new SimpleDateFormat("yyyyMMddhhmmssSSS").format(new Date());
        }

        // Bulk update multiple volumes to single export
        List<URI> volumeIds = Lists.newArrayList();
        for (Map.Entry<URI, Set<URI>> entry : addVolumeExports.entrySet()) {
            volumeIds.addAll(entry.getValue());
        }

        Map<URI, Integer> volumeHlus = getVolumeHLUs(volumeIds);

        for (Map.Entry<URI, Set<URI>> entry : addVolumeExports.entrySet()) {
            BlockStorageUtils.addVolumesToExport(entry.getValue(), currentHlu, entry.getKey(), volumeHlus, minPaths, maxPaths,
                    pathsPerInitiator, portGroup);
            logInfo("export.block.volume.add.existing", entry.getValue(), entry.getKey());
            if ((currentHlu != null) && (currentHlu > -1)) {
                currentHlu += entry.getValue().size();
            }
        }

        for (Map.Entry<URI, URI> entry : addComputeResourceToExports.entrySet()) {
            if (cluster != null) {
                BlockStorageUtils.addClusterToExport(entry.getKey(), cluster.getId(), minPaths, maxPaths, pathsPerInitiator, portGroup);
                logInfo("export.cluster.add.existing", entry.getValue(), entry.getKey());
            } else {
                BlockStorageUtils.addHostToExport(entry.getKey(), host.getId(), minPaths, maxPaths, pathsPerInitiator, portGroup);
                logInfo("export.host.add.existing", entry.getValue(), entry.getKey());
            }
        }

        // Create new export with multiple volumes that don't belong to an export
        if (!newVolumes.isEmpty()) {
            volumeHlus = getVolumeHLUs(newVolumes);
            URI exportId = null;
            if (cluster != null) {
            	 exportId = BlockStorageUtils.createClusterExport(projectId, virtualArrayId, newVolumes, currentHlu, cluster,
                        volumeHlus,
                        minPaths, maxPaths, pathsPerInitiator, portGroup);
            } else {
                exportId = BlockStorageUtils.createHostExport(projectId, virtualArrayId, newVolumes, currentHlu, host, volumeHlus,
                        minPaths, maxPaths, pathsPerInitiator, portGroup);
            }
            ExportGroupRestRep exportGroup = BlockStorageUtils.getExport(exportId);

            // add this export to the list of exports we will return to the caller
            exports.add(exportGroup);
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
    
    public static void precheckPortGroupParameter(URI resourceId, URI hostOrClusterId, URI projectId, URI vArrayId, URI portGroup) {
        boolean exportExist = false;
        if (BlockStorageUtils.isHost(hostOrClusterId)) {
            List<ExportGroupRestRep> exportGroups = BlockStorageUtils.findExportsContainingHost(hostOrClusterId, projectId, vArrayId);
            if (exportGroups != null && !exportGroups.isEmpty()) {
                exportExist = true;
            }
        } else if (BlockStorageUtils.isCluster(hostOrClusterId)) {
            List<ExportGroupRestRep> exportGroups = BlockStorageUtils.findExportsContainingCluster(hostOrClusterId, projectId, vArrayId);
            if (exportGroups != null && !exportGroups.isEmpty()) {
                exportExist = true;
            }
        }

        if (!exportExist && BlockStorageUtils.isVMAXUsePortGroupEnabled(resourceId) && portGroup == null) {
            ExecutionUtils.fail("failTask.exportPortGroupParameters.precheck", new Object[] {}, new Object[] {});
        }
    }
}
