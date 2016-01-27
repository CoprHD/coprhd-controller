/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.MigrationParam;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Task;

public class MigrateBlockVolumes extends ViPRExecutionTask<Task<VolumeRestRep>> {
    private final URI volumeId;
    private final URI sourceStorageSystem;
    private final URI targetVirtualPoolId;
    private final URI targetStorageSystem;

    public MigrateBlockVolumes(URI volumeId, URI sourceStorageSystem, URI targetVirtualPoolId, URI targetStorageSystem) {
        this.volumeId = volumeId;
        this.sourceStorageSystem = sourceStorageSystem;
        this.targetVirtualPoolId = targetVirtualPoolId;
        this.targetStorageSystem = targetStorageSystem;
        provideDetailArgs(volumeId, sourceStorageSystem, targetVirtualPoolId, targetStorageSystem);
    }

    @Override
    public Task<VolumeRestRep> executeTask() throws Exception {

        MigrationParam param = new MigrationParam();
        param.setSrcStorageSystem(sourceStorageSystem);
        param.setTgtStorageSystem(targetStorageSystem);
        param.setVirtualPool(targetVirtualPoolId);
        param.setVolume(volumeId);

        Task<VolumeRestRep> task = getClient().blockVolumes().migrate(param);
        addOrderIdTag(task.getTaskResource().getId());
        return task;
    }
}
