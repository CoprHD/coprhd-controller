/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.DiscoveredSystemObjectRestRep;

public class RegistrationFilter<T extends DiscoveredSystemObjectRestRep> extends DefaultResourceFilter<T> {
    public static final RegistrationFilter REGISTERED = new RegistrationFilter("REGISTERED");
    public static final RegistrationFilter UNREGISTERED = new RegistrationFilter("UNREGISTERED");

    private String registration;

    public RegistrationFilter(String registration) {
        this.registration = registration;
    }

    @Override
    public boolean accept(DiscoveredSystemObjectRestRep item) {
        return registration.equals(item.getRegistrationStatus());
    }
}
