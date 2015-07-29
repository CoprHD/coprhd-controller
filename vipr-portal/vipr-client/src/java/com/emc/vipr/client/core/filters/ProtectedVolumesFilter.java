/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.block.VolumeRestRep;

/**
 * Returns volumes that have protection enabled.
 */
public class ProtectedVolumesFilter extends DefaultResourceFilter<VolumeRestRep> {
    @Override
    public boolean accept(VolumeRestRep item) {
        return item.getProtection() != null && item.getProtection().getRpRep() != null &&
                "source".equalsIgnoreCase(item.getProtection().getRpRep().getPersonality());
    }
}
