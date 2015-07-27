/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.host.HostRestRep;

public class HostTypeFilter extends DefaultResourceFilter<HostRestRep> {
    public static final HostTypeFilter ESX = new HostTypeFilter("Esx");
    public static final HostTypeFilter WINDOWS = new HostTypeFilter("Windows");
    public static final HostTypeFilter LINUX = new HostTypeFilter("Linux");
    public static final HostTypeFilter AIX = new HostTypeFilter("AIX");
    
    private String type;

    public HostTypeFilter(String type) {
        this.type = type;
    }

    @Override
    public boolean accept(HostRestRep item) {
        return type.equals(item.getType());
    }
}
