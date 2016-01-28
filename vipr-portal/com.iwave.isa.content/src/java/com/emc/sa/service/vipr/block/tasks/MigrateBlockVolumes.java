/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.Set;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.MigrationParam;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.exceptions.ServiceErrorException;

public class MigrateBlockVolumes extends ViPRExecutionTask<Tasks<VolumeRestRep>> {
    private final Set<URI> volumeIds;
    private final URI sourceStorageSystem;
    private final URI targetVirtualPoolId;
    private final URI targetStorageSystem;

    public MigrateBlockVolumes(Set<URI> volumeIds, URI sourceStorageSystem, URI targetVirtualPoolId, URI targetStorageSystem) {
        this.volumeIds = volumeIds;
        this.sourceStorageSystem = sourceStorageSystem;
        this.targetVirtualPoolId = targetVirtualPoolId;
        this.targetStorageSystem = targetStorageSystem;
        provideDetailArgs(volumeIds, sourceStorageSystem, targetVirtualPoolId, targetStorageSystem);
    }

    @Override
    public Tasks<VolumeRestRep> executeTask() throws Exception {
        Tasks<VolumeRestRep> tasks = new Tasks<VolumeRestRep>(getClient().auth().getClient(), null,
                VolumeRestRep.class);
        for (URI volume : volumeIds) {
            MigrationParam param = new MigrationParam();
            param.setSrcStorageSystem(sourceStorageSystem);
            param.setTgtStorageSystem(targetStorageSystem);
            param.setVirtualPool(targetVirtualPoolId);
            param.setVolume(volume);

            try {
                Task<VolumeRestRep> task = getClient().blockVolumes().migrate(param);
                addOrderIdTag(task.getTaskResource().getId());
                tasks.getTasks().add(task);
            } catch (ServiceErrorException ex) {
                ExecutionUtils.currentContext().logError(ex.getDetailedMessage());
            }
        }
        return tasks;
    }
}
