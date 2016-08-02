/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.Fileingestorchestration.context.impl;

import com.emc.storageos.api.service.impl.resource.Fileingestorchestration.context.FileIngestionContext;
import com.emc.storageos.api.service.impl.resource.Fileingestorchestration.context.IngestionFileRequestContext;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;

import java.net.URI;
import java.util.List;

/**
 * Created by bonduj on 8/1/2016.
 */
public class MirrorFileIngestionContext implements IngestionFileRequestContext {


    @Override
    public StorageSystem getStorageSystem() {
        return null;
    }

    @Override
    public VirtualPool getVpool(UnManagedFileSystem unmanagedFileSystem) {
        return null;
    }

    @Override
    public VirtualArray getVarray(UnManagedFileSystem unmanagedFileSystem) {
        return null;
    }

    @Override
    public Project getProject() {
        return null;
    }

    @Override
    public TenantOrg getTenant() {
        return null;
    }

    @Override
    public UnManagedFileSystem getCurrentUnmanagedFileSystem() {
        return null;
    }

    @Override
    public URI getCurrentUnManagedFileSystemUri() {
        return null;
    }

    @Override
    public FileIngestionContext getFileContext() {
        return null;
    }

    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public UnManagedFileSystem next() {
        return null;
    }
}
