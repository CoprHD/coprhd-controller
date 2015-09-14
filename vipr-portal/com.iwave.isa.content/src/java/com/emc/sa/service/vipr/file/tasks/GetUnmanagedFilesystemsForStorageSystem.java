/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.NamedRelatedResourceRep;

public class GetUnmanagedFilesystemsForStorageSystem extends ViPRExecutionTask<List<NamedRelatedResourceRep>> {
    private URI storageSystem;

    public GetUnmanagedFilesystemsForStorageSystem(String storageSystem) {
        this(uri(storageSystem));
    }

    public GetUnmanagedFilesystemsForStorageSystem(URI storageSystem) {
        this.storageSystem = storageSystem;
        provideDetailArgs(storageSystem);
    }

    @Override
    public List<NamedRelatedResourceRep> executeTask() throws Exception {
        return getClient().unmanagedFileSystems().listByStorageSystem(storageSystem);
    }
}
