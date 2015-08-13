/*
 * Copyright (c) 2015. EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vplexcontroller;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPlacementDescriptor;

/**
 * This strategy assumes that the volumes have been divided amongst equally acceptable ExportMasks.
 */
public class VplexBackendVolumesToSeparateMasksStrategy implements VPlexBackendPlacementStrategy {
    public VplexBackendVolumesToSeparateMasksStrategy(DbClient dbClient, ExportMaskPlacementDescriptor descriptor) {
    }

    @Override
    public void execute() {
        // TODO: Fill in
    }
}
