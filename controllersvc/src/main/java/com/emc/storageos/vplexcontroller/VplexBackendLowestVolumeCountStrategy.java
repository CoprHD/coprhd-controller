/*
 * Copyright (c) 2015. EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vplexcontroller;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPlacementDescriptor;

/**
 * This VplexBackendPlacementStrategy will examine the ExportMaskPlacementDescriptor and pick the ExportMask that has the least volumes
 */
public class VplexBackendLowestVolumeCountStrategy implements VPlexBackendPlacementStrategy {
    private static final Logger log = LoggerFactory.getLogger(VplexBackendLowestVolumeCountStrategy.class);

    private DbClient dbClient;
    private ExportMaskPlacementDescriptor placementDescriptor;

    public VplexBackendLowestVolumeCountStrategy(DbClient dbClient, ExportMaskPlacementDescriptor descriptor) {
        this.dbClient = dbClient;
        this.placementDescriptor = descriptor;
    }

    @Override
    public void execute() {
        Map<URI, ExportMask> maskSetCopy = new HashMap<>(placementDescriptor.getMasks());

        // Determine the ExportMask with the lowest volume count
        int lowestVolumeCount = Integer.MAX_VALUE;
        ExportMask lowestCountMask = null;
        for (ExportMask mask : maskSetCopy.values()) {
            Integer volumeCount = 0;
            if (mask.getVolumes() != null) {
                volumeCount += mask.getVolumes().size();
            }
            if (mask.getExistingVolumes() != null) {
                volumeCount += mask.getExistingVolumes().size();
            }
            if (volumeCount < lowestVolumeCount) {
                lowestVolumeCount = volumeCount;
                lowestCountMask = mask;
            }
        }

        if (lowestCountMask != null) {
            for (URI uri : maskSetCopy.keySet()) {
                // Clean out any other ExportMask placements that were determined
                // to this singled out 'lowestCountMask'
                if (!uri.equals(lowestCountMask.getId())) {
                    placementDescriptor.invalidateExportMask(uri);
                }
            }

            URI tenant = placementDescriptor.getTenant();
            URI project = placementDescriptor.getProject();
            StorageSystem vplex = placementDescriptor.getVplex();
            StorageSystem array = placementDescriptor.getBackendArray();
            URI virtualArray = placementDescriptor.getVirtualArray();
            Collection<Initiator> initiators = placementDescriptor.getInitiators();

            // Determine ExportGroup
            ExportGroup exportGroup = ExportUtils.getVPlexExportGroup(dbClient, vplex, array, virtualArray, lowestCountMask, initiators,
                    tenant, project);
            placementDescriptor.mapExportMaskToExportGroup(lowestCountMask.getId(), exportGroup);
            log.info(String.format("Returning ExportMask %s (%s)", lowestCountMask.getMaskName(), lowestCountMask.getId()));
        } else {
            log.warn("Could not find ExportMask with the least volumes when trying to place volumes");
        }
    }
}
