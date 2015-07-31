/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import static com.emc.vipr.client.core.util.ResourceUtils.asString;
import static com.emc.vipr.client.core.util.ResourceUtils.id;

import java.net.URI;
import java.util.Set;

import com.emc.storageos.model.varray.NetworkRestRep;

/**
 * Filter for retrieving networks only associated with a particular virtual array.
 */
public class NetworkVirtualArrayFilter extends DefaultResourceFilter<NetworkRestRep> {
    private URI virtualArrayId;
    private String virtualArrayIdString;

    public NetworkVirtualArrayFilter(URI virtualArrayId) {
        this.virtualArrayId = virtualArrayId;
        this.virtualArrayIdString = asString(virtualArrayId);
    }

    @Override
    public boolean accept(NetworkRestRep item) {
        // 1.0 API
        if (virtualArrayId.equals(id(item.getVirtualArray()))) {
            return true;
        }
        // 1.1 API
        else {
            Set<String> varrays = item.getAssignedVirtualArrays();
            return (varrays != null) && varrays.contains(virtualArrayIdString);
        }
    }
}
