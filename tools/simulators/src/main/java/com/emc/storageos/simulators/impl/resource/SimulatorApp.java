/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.simulators.impl.resource;

import javax.ws.rs.core.Application;
import java.util.Set;

/**
 * Wrapper for BaseResource objects
 */
public class SimulatorApp extends Application {
    private Set<Object> _resource;

    public void setResource(Set<Object> resource) {
        _resource = resource;
    }

    @Override
    public Set<Object> getSingletons() {
        return _resource;
    }
}
