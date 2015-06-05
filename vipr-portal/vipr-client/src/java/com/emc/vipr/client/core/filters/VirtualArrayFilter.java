/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.block.export.ExportGroupRestRep;

import java.net.URI;

public class VirtualArrayFilter extends DefaultResourceFilter<ExportGroupRestRep> {
    private final URI virtualArrayId;

    public VirtualArrayFilter(URI virtualArrayId) {
        this.virtualArrayId = virtualArrayId;
    }

    @Override
    public boolean accept(ExportGroupRestRep item) {
        if (virtualArrayId != null && !item.getVirtualArray().getId().equals(virtualArrayId)) {
            return false;
        }

        return true;
    }
}
