/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.service.impl.resource;

import javax.ws.rs.core.Application;
import java.util.Set;

/**
 * Main application for authentication service
 */
public class AuthenticationApp extends Application {
    private Set<Object> _resource;

    public void setResource(Set<Object> resource) {
        _resource = resource;
    }

    @Override
    public Set<Object> getSingletons() {
        return _resource;
    }
}
