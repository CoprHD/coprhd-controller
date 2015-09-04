/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import com.emc.sa.service.vipr.tasks.DiscoverUnmanaged;

public class DiscoverUnmanagedVolumes extends DiscoverUnmanaged {
    public DiscoverUnmanagedVolumes(String storageSystemId) {
        super(storageSystemId, UnmanagedNamespace.UNMANAGED_VOLUMES);
    }
}
