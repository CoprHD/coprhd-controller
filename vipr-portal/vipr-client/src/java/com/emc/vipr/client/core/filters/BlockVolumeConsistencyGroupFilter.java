/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import java.net.URI;

import com.emc.storageos.model.block.VolumeRestRep;

/**
 * Filters block volumes that belong to a consistency group
 */
public class BlockVolumeConsistencyGroupFilter extends DefaultResourceFilter<VolumeRestRep> {
    private final URI consistencyGroup;
    private final boolean allowNullCg;

    /**
     * Creates a filter for volumes that belong to a consistency group
     * 
     * @param consistencyGroup the consistency group to check
     * @param allowNullCg if true, accept volumes that don't belong to a consistency group
     */
    public BlockVolumeConsistencyGroupFilter(URI consistencyGroup, boolean allowNullCg) {
        this.consistencyGroup = consistencyGroup;
        this.allowNullCg = allowNullCg;
    }

    @Override
    public boolean accept(VolumeRestRep item) {
        if (item.getConsistencyGroup() == null) {
            return allowNullCg;
        } else {
            return item.getConsistencyGroup().getId().equals(consistencyGroup);
        }
    }
}
