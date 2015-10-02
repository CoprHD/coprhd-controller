/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.asset.providers.FileProvider;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.UnManagedFileSystemRestRep;

public class GetUnmanagedFilesystems extends ViPRExecutionTask<List<UnManagedFileSystemRestRep>> {
    private final URI storageSystem;
    private final URI virtualPool;
    private final String type;

    public GetUnmanagedFilesystems(String storageSystem, String virtualPool, String type) {
        this(uri(storageSystem), uri(virtualPool), type);
    }

    public GetUnmanagedFilesystems(URI storageSystem, URI virtualPool, String type) {
        this.storageSystem = storageSystem;
        this.virtualPool = virtualPool;
        this.type = type;
        provideDetailArgs(storageSystem, virtualPool, type);
    }

    @Override
    public List<UnManagedFileSystemRestRep> executeTask() throws Exception {
        boolean exported = StringUtils.equalsIgnoreCase(type, FileProvider.EXPORTED_TYPE);
        return getClient().unmanagedFileSystems().getByStorageSystemVirtualPool(storageSystem, virtualPool, exported, null);
    }
}
