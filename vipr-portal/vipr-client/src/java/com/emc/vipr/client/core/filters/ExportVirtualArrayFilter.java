/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;

import java.net.URI;

/**
 * Filter for matching export groups based on a particular virtual array.
 */
public class ExportVirtualArrayFilter extends DefaultResourceFilter<ExportGroupRestRep> {
    private final URI virtualArrayId;

    public ExportVirtualArrayFilter(URI virtualArrayId) {
        this.virtualArrayId = virtualArrayId;
    }

    @Override
    public boolean accept(ExportGroupRestRep item) {
        if (virtualArrayId == null) {
            return true;
        }
        return virtualArrayId.equals(ResourceUtils.id(item.getVirtualArray()));
    }
}
