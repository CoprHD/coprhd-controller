/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.simulators.impl.resource;

import com.emc.storageos.simulators.ObjectStore;

/**
 * Base resource with object store
 */
public abstract class BaseResource {
    protected ObjectStore       _objectStore;

    public void setObjectStore(ObjectStore objectStore) {
        _objectStore = objectStore;
    }
}

