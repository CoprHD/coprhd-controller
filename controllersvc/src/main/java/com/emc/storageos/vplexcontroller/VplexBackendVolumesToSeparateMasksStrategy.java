/*
 * Copyright (c) 2015. EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vplexcontroller;

import static com.emc.storageos.util.ExportUtils.getVPlexExportGroup;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPlacementDescriptor;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;

/**
 * This strategy assumes that the volumes have been divided amongst equally acceptable ExportMasks.
 */
public class VplexBackendVolumesToSeparateMasksStrategy implements VPlexBackendPlacementStrategy {
    private static Logger log = LoggerFactory.getLogger(VplexBackendVolumesToSeparateMasksStrategy.class);

    private final DbClient dbClient;
    private final ExportMaskPlacementDescriptor placementDescriptor;

    public VplexBackendVolumesToSeparateMasksStrategy(DbClient dbClient, ExportMaskPlacementDescriptor descriptor) {
        this.dbClient = dbClient;
        this.placementDescriptor = descriptor;
    }

    /**
     * Placement descriptor should have a set of Masks that match. We will get the ExportGroup that should be
     * associated with the ExportMask and map them. This will allow the VPlex backend workflow to create
     * the AddVolume steps required for each ExportGroup+ExportMask combination.
     */
    @Override
    public void execute() {
        // https://coprhd.atlassian.net/browse/COP-19956
        // Examine the placement descriptor and see if it shows that the same volumes are being placed
        // against different ExportMasks, but these ExportMasks point to the same VPlex cluster. If so,
        // we want to place the volume into a single ExportMask instead.
        Map<URI, Set<URI>> volumeToMasks = createVolumeToMasksMap();
        Set<URI> multiplyPlacedVolumes = new HashSet<>(); // Filled in by anyVolumesPlacedToMultipleMasks()
        if (anyVolumesPlacedToMultipleMasks(multiplyPlacedVolumes, volumeToMasks)) {
            // Determine if the masks that the volumes are multiple placed against, point to the same
            // VPlex cluster. If they do, then we shall adjust placement to select the ExportMask
            // that has the least number of total volumes.
            for (URI volumeURI : multiplyPlacedVolumes) {
                Set<URI> placedMasks = volumeToMasks.get(volumeURI);
                Map<URI, ExportMask> exportMaskMap = new HashMap<>(); // Filled in by masksAreForSameVPlexCluster()
                if (masksAreForSameVPlexCluster(exportMaskMap, placedMasks)) {
                    placeVolumeToMaskWithLeastNumberOfVolumes(volumeURI, exportMaskMap);
                }
            }
        }

        // For each ExportMask URI to ExportMask entry, lookup the ExportGroup associated with it and map it
        Map<URI, ExportMask> maskSetCopy = new HashMap<>(placementDescriptor.getMasks());
        for (Map.Entry<URI, ExportMask> entry : maskSetCopy.entrySet()) {
            URI exportMaskURI = entry.getKey();
            ExportMask exportMask = entry.getValue();
            // Get contextual information from the placement
            URI tenant = placementDescriptor.getTenant();
            URI project = placementDescriptor.getProject();
            StorageSystem vplex = placementDescriptor.getVplex();
            StorageSystem array = placementDescriptor.getBackendArray();
            URI virtualArray = placementDescriptor.getVirtualArray();
            Collection<Initiator> initiators = placementDescriptor.getInitiators();

            // Determine ExportGroup
            ExportGroup exportGroup = getVPlexExportGroup(dbClient, vplex, array, virtualArray, exportMask, initiators, tenant, project);
            placementDescriptor.mapExportMaskToExportGroup(exportMaskURI, exportGroup);
        }
    }

    /**
     * Routine will adjust the placementDescriptor.placedVolumes, such that the volume will be placed only to one ExportMask (the one
     * with least number of volumes).
     *
     * NB:
     * This routine is to be run in the context of determining volumes that are placed against multiple ExportMasks. The ExportMasks
     * would be pointing to the same cluster, so only a single ExportMask would be required. So, the placedMasks map should contain
     * ExportMasks that point to the same VPlex cluster.
     *
     * @param volumeURI [IN] - Volume URI
     * @param placedMasks [IN] - Mapping of ExportMask URI to ExportMask object
     */
    private void placeVolumeToMaskWithLeastNumberOfVolumes(URI volumeURI, Map<URI, ExportMask> placedMasks) {
        log.info("These exportMasks are pointing to the same cluster: {}", placedMasks.keySet());
        // We're going to try the smallest number of volumes in the ExportMask, so start
        // with the largest possible count size
        int leastNumberOfVolumes = Integer.MAX_VALUE;
        // As we go through the ExportMask URIs, save off those that don't have the least
        // number of volumes
        Set<URI> exportMaskWithMoreVolumes = new HashSet<>();
        ExportMask currMaskWithLeastVolumes = null;
        for (ExportMask mask : placedMasks.values()) {
            // Try to determine the ExportMask with the least number of total volumes.
            int totalVolumeCount = mask.returnTotalVolumeCount();
            if (totalVolumeCount < leastNumberOfVolumes) {
                if (currMaskWithLeastVolumes != null) {
                    exportMaskWithMoreVolumes.add(currMaskWithLeastVolumes.getId());
                }
                leastNumberOfVolumes = totalVolumeCount;
                currMaskWithLeastVolumes = mask;
            } else {
                exportMaskWithMoreVolumes.add(mask.getId());
            }
        }
        if (currMaskWithLeastVolumes != null) {
            log.info(String.format("ExportMask %s was selected for volume %s, as it has %d total volumes",
                    currMaskWithLeastVolumes.getId(), volumeURI, currMaskWithLeastVolumes.returnTotalVolumeCount()));
        }
        log.info("Determined that this volume {} can be unplaced from these ExportMasks: {}", volumeURI, exportMaskWithMoreVolumes);
        log.info("placeVolumeToMaskWithLeastNumberOfVolumes - PlacementDescriptor before:\n{}", placementDescriptor.toString());
        // For any export masks that were found to have more (or the same) number of volumes as
        // the ExportMask with the least, invalidate its placement in the descriptor
        for (URI exportMaskURI : exportMaskWithMoreVolumes) {
            placementDescriptor.unplaceVolumeFromMask(volumeURI, exportMaskURI);
        }
        log.info("placeVolumeToMaskWithLeastNumberOfVolumes - PlacementDescriptor after:\n{}", placementDescriptor.toString());
    }

    /**
     * Given a set of ExportMask URIs, determines if the ExportMasks that they represent are associated with the same VPlex cluster.
     * 
     * @param exportMaskMap [OUT] - Mapping of ExportMask URI to ExportMask object
     * @param placedMasks [IN] - ExportMask URIs
     * @return true, iff the ExportMasks referenced by 'placedMasks' point to the same cluster
     */
    private boolean masksAreForSameVPlexCluster(Map<URI, ExportMask> exportMaskMap, Set<URI> placedMasks) {
        // Get a mapping of port WWN to the cluster
        Map<String, String> wwnToClusterID = VPlexUtil.getPortIdToClusterMap(dbClient, placementDescriptor.getVplex());
        Set<String> clusterIDsFound = new HashSet<>();
        Iterator<ExportMask> exportMaskIterator = dbClient.queryIterativeObjects(ExportMask.class, placedMasks);
        // Iterator through the list of masks that indicate that have the
        // same volumes placed against them.
        while (exportMaskIterator.hasNext()) {
            ExportMask exportMask = exportMaskIterator.next();
            exportMaskMap.put(exportMask.getId(), exportMask);
            Set<URI> initiatorURIs = ExportMaskUtils.getAllInitiatorsForExportMask(dbClient, exportMask);
            Iterator<Initiator> initiatorIterator = dbClient.queryIterativeObjects(Initiator.class, initiatorURIs);
            // Iterate through the list of initiators for the ExportMask.
            // Find the clusterID associated with it, then add it to the
            // clusterIDsFound set.
            while (initiatorIterator.hasNext()) {
                Initiator initiator = initiatorIterator.next();
                String clusterID = wwnToClusterID.get(Initiator.normalizePort(initiator.getInitiatorPort()));
                if (clusterID != null) {
                    clusterIDsFound.add(clusterID);
                }
            }
        }
        log.info("The following clusters: {} are associated with these ExportMasks: {}", clusterIDsFound, placedMasks);
        return clusterIDsFound.size() == 1;
    }

    /**
     * Routine will examine the volumeToMasks map to see if there are any volumes that placed to multiple ExportMasks.
     * If any, it returns true and the 'multiplePlaced' set will contain the volume URIs that are multiply placed.
     * 
     * @param multiplyPlaced [OUT] - Set of Volume URIs that are multiple placed
     * @param volumeToMasks [IN] - Mapping of volume URI to ExportMask URIs
     * @return true, iff we found at least one volume that's placed to multiple ExportMasks.
     */
    private boolean anyVolumesPlacedToMultipleMasks(Set<URI> multiplyPlaced, Map<URI, Set<URI>> volumeToMasks) {
        boolean atLeastOneVolumePlacedToMultipleMasks = false;
        for (URI volumeURI : volumeToMasks.keySet()) {
            boolean placedToMultipleMasks = volumeToMasks.get(volumeURI).size() > 1;
            if (placedToMultipleMasks) {
                multiplyPlaced.add(volumeURI);
                atLeastOneVolumePlacedToMultipleMasks = true;
            }
        }
        return atLeastOneVolumePlacedToMultipleMasks;
    }

    /**
     * Effectively, this routine reverses the placementDescriptor.placedVolumes map, which maps an ExportMask URI to Volumes.
     * Here we will generate a map of Volume URI to set of ExportMask URIs.
     * 
     * @return Map of Volume URI to set of ExportMask URIs.
     */
    private Map<URI, Set<URI>> createVolumeToMasksMap() {
        Map<URI, Set<URI>> volumeToMasks = new HashMap<>();
        // For each ExportMask URI found ...
        for (URI maskURI : placementDescriptor.getMasks().keySet()) {
            Map<URI, Volume> volumeMap = placementDescriptor.getPlacedVolumes(maskURI);
            // If there are any volumes placed to this ExportMask, iterate through
            // them and associate the ExportMask URI to the volume.
            for (URI volumeURI : volumeMap.keySet()) {
                Set<URI> masks = volumeToMasks.get(volumeURI);
                if (masks == null) {
                    masks = new HashSet<>();
                    volumeToMasks.put(volumeURI, masks);
                }
                masks.add(maskURI);
            }
        }
        return volumeToMasks;
    }
}
