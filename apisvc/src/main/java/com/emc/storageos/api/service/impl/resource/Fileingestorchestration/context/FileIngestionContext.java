/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.Fileingestorchestration.context;

import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;

/**
 * Created by bonduj on 8/1/2016.
 */
public interface FileIngestionContext {
    public UnManagedFileSystem getCurrentUnmanagedFileSystem();
}
