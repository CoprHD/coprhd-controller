/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.UnManagedVolumeRestRep;

public class GetUnmanagedVolumesByHostOrCluster extends ViPRExecutionTask<List<UnManagedVolumeRestRep>> {
    private URI hostOrClusterId;

    public GetUnmanagedVolumesByHostOrCluster(String hostOrClusterId) {
        this(uri(hostOrClusterId));
    }

    public GetUnmanagedVolumesByHostOrCluster(URI hostOrClusterId) {
        this.hostOrClusterId = hostOrClusterId;
        provideDetailArgs(hostOrClusterId);
    }

    @Override
    public List<UnManagedVolumeRestRep> executeTask() throws Exception {
        if (BlockStorageUtils.isHost(hostOrClusterId)) {
            return getClient().unmanagedVolumes().getByHost(hostOrClusterId);
        }
        else {
            return getClient().unmanagedVolumes().getByCluster(hostOrClusterId);
        }
    }
}
