/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.DiscoveredSystemObjectRestRep;

public class DiscoveryStatusFilter<T extends DiscoveredSystemObjectRestRep> extends DefaultResourceFilter<T> {
    public static final DiscoveryStatusFilter COMPLETE = new DiscoveryStatusFilter("COMPLETE");
    public static final DiscoveryStatusFilter ERROR = new DiscoveryStatusFilter("ERROR");
    public static final DiscoveryStatusFilter UNKNOWN = new DiscoveryStatusFilter("UNKNOWN");

    private String compatibility;

    public DiscoveryStatusFilter(String compatibility) {
        this.compatibility = compatibility;
    }

    @Override
    public boolean accept(DiscoveredSystemObjectRestRep item) {
        return compatibility.equals(item.getDiscoveryJobStatus());
    }
}
