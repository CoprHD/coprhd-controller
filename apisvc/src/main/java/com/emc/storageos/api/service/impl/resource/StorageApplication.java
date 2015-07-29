/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
