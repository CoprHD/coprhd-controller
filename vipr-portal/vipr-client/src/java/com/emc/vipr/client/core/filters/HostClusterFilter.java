/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import java.net.URI;

import com.emc.storageos.model.host.HostRestRep;

public class HostClusterFilter extends DefaultResourceFilter<HostRestRep> {
    private URI id;

    public HostClusterFilter(URI id) {
        this.id = id;
    }

    @Override
    public boolean accept(HostRestRep item) {
        if (item.getCluster() == null) {
            return false;
        }
        return id.equals(item.getCluster().getId());
    }
}
