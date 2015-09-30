/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.UnManagedFileSystemRestRep;

public class GetUnmanagedFilesystems extends ViPRExecutionTask<List<UnManagedFileSystemRestRep>> {
    private final URI storageSystem;
    private final URI virtualPool;

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
        return getClient().unmanagedFileSystems().getByStorageSystemVirtualPool(storageSystem, virtualPool, null);
    }
}
