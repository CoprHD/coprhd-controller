/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.UnManagedFileSystemRestRep;
import com.emc.vipr.client.core.filters.UnmanagedFileSystemVirtualPoolFilter;

public class GetUnmanagedFilesystems extends ViPRExecutionTask<List<UnManagedFileSystemRestRep>> {
    private URI storageSystem;
    private URI virtualPool;

    public GetUnmanagedFilesystems(String storageSystem, String virtualPool) {
        this(uri(storageSystem), uri(virtualPool));
    }

    public GetUnmanagedFilesystems(URI storageSystem, URI virtualPool) {
        this.storageSystem = storageSystem;
        this.virtualPool = virtualPool;
        provideDetailArgs(storageSystem, virtualPool);
    }

    @Override
    public List<UnManagedFileSystemRestRep> executeTask() throws Exception {
        UnmanagedFileSystemVirtualPoolFilter filter = new UnmanagedFileSystemVirtualPoolFilter(virtualPool);
        return getClient().unmanagedFileSystems().getByStorageSystem(storageSystem, filter);
    }
}
