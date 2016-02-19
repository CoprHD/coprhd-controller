/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.collectionString;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Volume;
import com.google.common.collect.Sets;

/**
 * Utility class for ExportMaskPlacementDescriptor. Class should have higher-order functionality
 * than the basic ExportMaskPlacementDescriptor manipulators. It's also good to have this class,
 * so that it can be invoked for unit testing.
 */
public final class ExportMaskPlacementDescriptorHelper {
    private static final Logger log = LoggerFactory.getLogger(ExportMaskPlacementDescriptorHelper.class);

    /**
     * This is intended to be a utility class, so make the constructor private
     */
    private ExportMaskPlacementDescriptorHelper() {

    }

    /**
     * If there are any invalidMasks, try to determine if we can use alternative and equivalent ExportMasks.
     * If so, we will use them for placement.
     *
     * @param descriptor [IN/OUT] - Context used for backend placement
     * @param invalidMasks [IN] - Set of ExportMasks that were original suggested, but look to be invalid based on VPlex
     *            criteria.
     */
    public static void putUnplacedVolumesIntoAlternativeMask(ExportMaskPlacementDescriptor descriptor, Set<URI> invalidMasks) {
        // First check: are there any unplaced volumes?
        if (!descriptor.hasUnPlacedVolumes()) {
            // Nope - nothing to do here.
            return;
        }

        log.info("Trying to see if there are any alternative exports that can be used for unplaced volumes...");

        // All the volumes to be placed: need this to look up the Volume object
        Map<URI, Volume> volumes = descriptor.getVolumesToPlace();

        // The replacement map: this represents a remapping of the volume
        // to equivalent, alternative ExportMasks
        Map<URI, Map<URI, Volume>> replaceMap = new HashMap<>();

        // Go through all the unplaced volumes ...
        for (URI volumeURI : descriptor.getUnplacedVolumes().keySet()) {
            // Get the set of ExportMasks that are equivalent, but perhaps not originally placed for the volume.
            Set<URI> equivalentExportsForVolume = descriptor.getAlternativeExportsForVolume(volumeURI);
            log.info("Found these ExportMasks are alternatives for volume {}: {}", volumeURI,
                    collectionString(equivalentExportsForVolume));
            // Go through all the invalid exports ...
            for (URI invalidExportURI : invalidMasks) {
                // Get equivalents for the invalid ExportMask (if any)
                Set<URI> equivalentExportMask = descriptor.getEquivalentExportMasks(invalidExportURI);
                log.info("Found these ExportMasks are equivalent for invalid export {}: {}", invalidExportURI,
                        collectionString(equivalentExportMask));
                // Find the ExportMasks that are the intersection of the equivalent ExportMasks for
                // the invalid export *and* the set of ExportMasks that are considered alternatives
                // for the volume.
                Set<URI> toChooseFrom = Sets.intersection(equivalentExportsForVolume, equivalentExportMask);
                if (!toChooseFrom.isEmpty()) {
                    // Find the ExportMask with the least number of volumes
                    URI selectedURI = getExportMaskWithLeastVolumes(descriptor, toChooseFrom);
                    log.info("Common: {} - Least volumes: {}", collectionString(toChooseFrom), selectedURI);
                    // Update the replacement mapping for this ExportMask
                    Map<URI, Volume> volumeMap = replaceMap.get(selectedURI);
                    if (volumeMap == null) {
                        volumeMap = new HashMap<>();
                        replaceMap.put(selectedURI, volumeMap);
                    }
                    volumeMap.put(volumeURI, volumes.get(volumeURI));
                }
            }
        }

        // Go through the replacement mapping
        for (Map.Entry<URI, Map<URI, Volume>> replaceEntry : replaceMap.entrySet()) {
            descriptor.placeVolumes(replaceEntry.getKey(), replaceEntry.getValue());
        }

        log.info("After trying to find invalid mask alternatives:\n{}", descriptor);
    }

    /**
     * Given a set of ExportMask URIs, which are associated with the 'descriptor', return the URI of the
     * ExportMask that has the least number of volumes.
     *
     * @param descriptor [IN/OUT] - Context used for backend placement
     * @param exportMaskURIs [IN] - Set of ExportMask URIs to search through
     * @return URI of ExportMask that has the least number of volumes amongst those in the 'exportMaskURIs' set.
     */
    public static URI getExportMaskWithLeastVolumes(ExportMaskPlacementDescriptor descriptor, Set<URI> exportMaskURIs) {
        URI foundExportMaskURI = null;
        if (exportMaskURIs.size() == 1) {
            return exportMaskURIs.iterator().next();
        }
        Integer currentLeastVolumes = Integer.MAX_VALUE;
        for (URI exportMaskURI : exportMaskURIs) {
            ExportMask exportMask = descriptor.getExportMask(exportMaskURI);
            int volumeCount = exportMask.returnTotalVolumeCount();
            if (volumeCount < currentLeastVolumes) {
                currentLeastVolumes = volumeCount;
                foundExportMaskURI = exportMaskURI;
            }
        }
        return foundExportMaskURI;
    }
}
