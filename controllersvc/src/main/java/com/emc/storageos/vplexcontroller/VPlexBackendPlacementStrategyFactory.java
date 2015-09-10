/*
 * Copyright (c) 2015. EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vplexcontroller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPlacementDescriptor;

/**
 * VplexBackendPlacementStrategy creation factory. Will take in contextual information to create an appropriate
 * VPlexBackendPlacementStrategy implementation
 *
 */
public class VPlexBackendPlacementStrategyFactory {
    private static final Logger log = LoggerFactory.getLogger(VPlexBackendPlacementStrategy.class);

    static VPlexBackendPlacementStrategy create(DbClient dbClient, ExportMaskPlacementDescriptor descriptor) {
        VPlexBackendPlacementStrategy strategy;
        switch (descriptor.getPlacementHint()) {
            case VOLUMES_TO_SINGLE_MASK:
                strategy = new VplexBackendLowestVolumeCountStrategy(dbClient, descriptor);
                break;
            case VOLUMES_TO_SEPARATE_MASKS:
                strategy = new VplexBackendVolumesToSeparateMasksStrategy(dbClient, descriptor);
                break;
            default:
                strategy = new VplexBackendLowestVolumeCountStrategy(dbClient, descriptor);
                log.error("Unexpected placement strategy {}, will use VplexBackendLowestVolumeCountStrategy",
                        descriptor.getPlacementHint().name());
                break;
        }
        log.info("VplexBackendPlacementStrategy selected {}", strategy.getClass().getSimpleName());
        return strategy;
    }
}
