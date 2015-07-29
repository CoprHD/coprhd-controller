/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.resource;

import javax.ws.rs.core.Application;
import java.util.Set;

public class SysSvcApp extends Application {
    private Set<Object> _resource;

    public void setResource(Set<Object> resource) {
        _resource = resource;
    }

    @Override
    public Set<Object> getSingletons() {
        return _resource;
    }
}
