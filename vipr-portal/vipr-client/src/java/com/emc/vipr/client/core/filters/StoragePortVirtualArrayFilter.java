/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import static com.emc.vipr.client.core.util.ResourceUtils.asString;

import java.net.URI;
import java.util.Set;

import com.emc.storageos.model.ports.StoragePortRestRep;

/**
 * Filter for retrieving storage ports only associated with a particular virtual array.
 */
public class StoragePortVirtualArrayFilter extends DefaultResourceFilter<StoragePortRestRep> {
    private String virtualArrayId;

    public StoragePortVirtualArrayFilter(URI virtualArrayId) {
        this.virtualArrayId = asString(virtualArrayId);
    }

    @Override
    public boolean accept(StoragePortRestRep item) {
        Set<String> varrays = item.getAssignedVirtualArrays();
        return (varrays != null) && varrays.contains(virtualArrayId);
    }
}
