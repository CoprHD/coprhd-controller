/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.block.VolumeRestRep;

public class VplexVolumeFilter extends DefaultResourceFilter<VolumeRestRep> {
    @Override
    public boolean accept(VolumeRestRep item) {
        return item.getHaVolumes() != null && !item.getHaVolumes().isEmpty();
    }
}