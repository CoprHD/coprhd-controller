/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.RelatedResourceRep;

public class GetUnmanagedVolumesForStorageSystem extends ViPRExecutionTask<List<RelatedResourceRep>> {
    private URI storageSystem;

    public GetUnmanagedVolumesForStorageSystem(String storageSystem) {
        this(uri(storageSystem));
    }

    public GetUnmanagedVolumesForStorageSystem(URI storageSystem) {
        this.storageSystem = storageSystem;
        provideDetailArgs(storageSystem);
    }

    @Override
    public List<RelatedResourceRep> executeTask() throws Exception {
        return getClient().unmanagedVolumes().listByStorageSystem(storageSystem);
    }
}
