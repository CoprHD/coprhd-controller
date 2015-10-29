/*
 * Copyright (c) 2015. EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vplexcontroller;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPlacementDescriptor;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.emc.storageos.util.ExportUtils.*;

/**
 * This strategy assumes that the volumes have been divided amongst equally acceptable ExportMasks.
 */
public class VplexBackendVolumesToSeparateMasksStrategy implements VPlexBackendPlacementStrategy {
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
}
