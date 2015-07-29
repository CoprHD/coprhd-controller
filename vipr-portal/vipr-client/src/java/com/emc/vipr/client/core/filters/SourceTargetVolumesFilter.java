/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.block.VolumeRestRep;

/**
 * Filters volumes to return all volumes that are either:
 * - Recoverpoint source volumes
 * - Recoverpoint target volumes
 * - Non-recoverpoint volume
 */
public class SourceTargetVolumesFilter extends DefaultResourceFilter<VolumeRestRep> {
    @Override
    public boolean accept(VolumeRestRep item) {
        return item.getProtection() == null ||
                item.getProtection().getRpRep() == null ||
                item.getProtection().getRpRep().getPersonality() == null ||
                item.getProtection().getRpRep().getPersonality().equalsIgnoreCase("source") ||
                item.getProtection().getRpRep().getPersonality().equalsIgnoreCase("target");
    }
}
