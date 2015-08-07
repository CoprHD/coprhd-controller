/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import java.net.URI;

import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.vipr.client.Task;

/**
 * @author Chris Dail
 */
public class DiscoverUnmanaged extends WaitForTask<StorageSystemRestRep> {

    private URI storageSystemId;
    private UnmanagedNamespace unmanagedNamespace;

    public DiscoverUnmanaged(String storageSystemId, UnmanagedNamespace namespace) {
        this(uri(storageSystemId), namespace);
    }

    public DiscoverUnmanaged(URI storageSystemId, UnmanagedNamespace namespace) {
        this.storageSystemId = storageSystemId;
        this.unmanagedNamespace = namespace;
        provideDetailArgs(storageSystemId, unmanagedNamespace);
    }

    @Override
    protected Task<StorageSystemRestRep> doExecute() throws Exception {
        return getClient().storageSystems().discover(storageSystemId, unmanagedNamespace.toString());
    }

    public static enum UnmanagedNamespace {
        UNMANAGED_VOLUMES, UNMANAGED_FILESYSTEMS, ALL
    }
}
