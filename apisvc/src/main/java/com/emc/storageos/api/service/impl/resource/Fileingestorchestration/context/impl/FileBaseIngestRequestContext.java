/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.Fileingestorchestration.context.impl;

import com.emc.storageos.api.service.impl.resource.Fileingestorchestration.context.FileIngestionContext;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;

/**
 * Created by bonduj on 8/1/2016.
 */
public class FileBaseIngestRequestContext implements FileIngestionContext {

    @Override
    public UnManagedFileSystem getCurrentUnmanagedFileSystem() {
        return null;
    }
}
