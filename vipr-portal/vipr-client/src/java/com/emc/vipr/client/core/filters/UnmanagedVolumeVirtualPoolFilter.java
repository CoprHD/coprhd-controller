/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import static com.emc.vipr.client.core.util.UnmanagedHelper.getVpoolsForUnmanaged;

import java.net.URI;
import java.util.Set;

import com.emc.storageos.model.block.UnManagedVolumeRestRep;

public class UnmanagedVolumeVirtualPoolFilter extends DefaultResourceFilter<UnManagedVolumeRestRep> {
    private URI virtualPoolId;

    public UnmanagedVolumeVirtualPoolFilter(URI virtualPoolId) {
        this.virtualPoolId = virtualPoolId;
    }

    @Override
    public boolean accept(UnManagedVolumeRestRep item) {
        if (!super.accept(item)) {
            return false;
        }

        Set<URI> vpools = getVpoolsForUnmanaged(item.getVolumeCharacteristics(), item.getSupportedVPoolUris());
        return vpools.contains(virtualPoolId);
    }
}
