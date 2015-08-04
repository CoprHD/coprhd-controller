/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.vipr.client.core.util.CachedResources;
import com.emc.vipr.client.core.util.VirtualPoolUtils;

public class VplexVolumeVirtualPoolFilter extends DefaultResourceFilter<VolumeRestRep> {
    private final CachedResources<BlockVirtualPoolRestRep> blockVpools;

    public VplexVolumeVirtualPoolFilter(CachedResources<BlockVirtualPoolRestRep> blockVpools) {
        this.blockVpools = blockVpools;
    }

    @Override
    public boolean accept(VolumeRestRep item) {
        BlockVirtualPoolRestRep vpool = blockVpools.get(item.getVirtualPool());
        return VirtualPoolUtils.isHighAvailability(
                vpool.getHighAvailability());
    }
}