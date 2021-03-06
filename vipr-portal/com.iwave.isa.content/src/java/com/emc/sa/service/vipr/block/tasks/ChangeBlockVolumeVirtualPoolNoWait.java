/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.VolumeVirtualPoolChangeParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import com.google.common.collect.Lists;

public class ChangeBlockVolumeVirtualPoolNoWait extends ViPRExecutionTask<Tasks<VolumeRestRep>> {
    // map of vpool -> set of volumes
    private final Map<URI, Set<URI>> volumeIds;
    private final URI targetVirtualPoolId;

    public ChangeBlockVolumeVirtualPoolNoWait(Map<URI, Set<URI>> volumeIds, URI targetVirtualPoolId) {
        this.volumeIds = volumeIds;
        this.targetVirtualPoolId = targetVirtualPoolId;
        provideDetailArgs(volumeIds, targetVirtualPoolId);
    }

    @Override
    public Tasks<VolumeRestRep> executeTask() throws Exception {
        Tasks<VolumeRestRep> result = new Tasks<VolumeRestRep>(getClient().auth().getClient(), null,
                VolumeRestRep.class);

        // One request per virtual pool
        for (URI vpool : volumeIds.keySet()) {
            if (!vpool.equals(targetVirtualPoolId)) {
                VolumeVirtualPoolChangeParam input = new VolumeVirtualPoolChangeParam();
                input.setVolumes(Lists.newArrayList(volumeIds.get(vpool)));
                input.setVirtualPool(targetVirtualPoolId);
                try {
                    Tasks<VolumeRestRep> tasks = getClient().blockVolumes().changeVirtualPool(input);
                    for (Task<VolumeRestRep> task : tasks.getTasks()) {
                        addOrderIdTag(task.getTaskResource().getId());
                    }
                    result.getTasks().addAll(tasks.getTasks());
                } catch (ServiceErrorException ex) {
                    logError(ex.getMessage());
                }
            }
        }
        return result;
    }
}
