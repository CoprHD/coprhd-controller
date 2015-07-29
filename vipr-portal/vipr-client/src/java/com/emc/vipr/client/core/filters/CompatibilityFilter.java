/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.DiscoveredSystemObjectRestRep;

public class CompatibilityFilter<T extends DiscoveredSystemObjectRestRep> extends DefaultResourceFilter<T> {
    public static final CompatibilityFilter COMPATIBLE = new CompatibilityFilter("COMPATIBLE");
    public static final CompatibilityFilter INCOMPATIBLE = new CompatibilityFilter("INCOMPATIBLE");
    public static final CompatibilityFilter UNKNOWN = new CompatibilityFilter("UNKNOWN");

    private String compatibility;

    public CompatibilityFilter(String compatibility) {
        this.compatibility = compatibility;
    }

    @Override
    public boolean accept(DiscoveredSystemObjectRestRep item) {
        return compatibility.equals(item.getCompatibilityStatus());
    }
}
