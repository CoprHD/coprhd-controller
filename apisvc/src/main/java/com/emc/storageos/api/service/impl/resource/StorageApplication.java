/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import javax.ws.rs.core.Application;
import java.util.Set;

/**
 * Wraps device service singletons for JAX-RS
 */
public class StorageApplication extends Application {
    private Set<Object> _resource;

    public void setResource(Set<Object> resource) {
        _resource = resource;
    }

    @Override
    public Set<Object> getSingletons() {
        return _resource;
    }
}
