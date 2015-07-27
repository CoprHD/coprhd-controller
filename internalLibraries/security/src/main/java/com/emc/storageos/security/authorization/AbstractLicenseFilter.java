/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authorization;

import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilter;

/**
 * Abstract filter class for license checks
 */
public abstract class AbstractLicenseFilter implements ResourceFilter, ContainerRequestFilter {

    @Override
    public ContainerRequestFilter  getRequestFilter() {
        return this;
    }

    @Override
    public ContainerResponseFilter getResponseFilter() {
        return null;
    }
}
