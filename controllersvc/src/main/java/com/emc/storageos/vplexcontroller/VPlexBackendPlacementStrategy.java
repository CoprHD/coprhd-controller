/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vplexcontroller;

/**
 * Strategy interface for implementing different ways to select
 * matched ExportMasks for backend arrays of a VPlex system.
 */
interface VPlexBackendPlacementStrategy {
            void execute();
}
