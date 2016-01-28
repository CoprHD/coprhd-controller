/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.Collection;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.VolumeVirtualPoolChangeParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;

public class ChangeBlockVolumeVirtualPoolNoWait extends ViPRExecutionTask<Tasks<VolumeRestRep>> {
    private final Collection<URI> volumeIds;
    private final URI targetVirtualPoolId;

    public ChangeBlockVolumeVirtualPoolNoWait(Collection<URI> volumeIds, URI targetVirtualPoolId) {
        this.volumeIds = volumeIds;
        this.targetVirtualPoolId = targetVirtualPoolId;
        provideDetailArgs(volumeIds, targetVirtualPoolId);
    }

    @Override
    public Tasks<VolumeRestRep> executeTask() throws Exception {
        VolumeVirtualPoolChangeParam input = new VolumeVirtualPoolChangeParam();
        input.setVolumes(Lists.newArrayList(this.volumeIds));
        input.setVirtualPool(targetVirtualPoolId);
        Tasks<VolumeRestRep> tasks = getClient().blockVolumes().changeVirtualPool(input);
        for (Task<VolumeRestRep> task : tasks.getTasks()) {
            addOrderIdTag(task.getTaskResource().getId());
        }
        return tasks;
    }
}
