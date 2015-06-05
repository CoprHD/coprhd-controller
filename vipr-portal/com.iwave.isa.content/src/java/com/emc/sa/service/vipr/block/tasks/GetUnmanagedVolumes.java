/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.UnManagedVolumeRestRep;
import com.emc.vipr.client.core.filters.UnmanagedVolumeVirtualPoolFilter;

public class GetUnmanagedVolumes extends ViPRExecutionTask<List<UnManagedVolumeRestRep>> {
    private URI storageSystem;
    private URI virtualPool;

    public GetUnmanagedVolumes(String storageSystem, String virtualPool) {
        this(uri(storageSystem), uri(virtualPool));
    }

    public GetUnmanagedVolumes(URI storageSystem, URI virtualPool) {
        this.storageSystem = storageSystem;
        this.virtualPool = virtualPool;
        provideDetailArgs(storageSystem, virtualPool);
    }

    @Override
    public List<UnManagedVolumeRestRep> executeTask() throws Exception {
        UnmanagedVolumeVirtualPoolFilter filter = new UnmanagedVolumeVirtualPoolFilter(virtualPool);
        return getClient().unmanagedVolumes().getByStorageSystem(storageSystem, filter);
    }
}
