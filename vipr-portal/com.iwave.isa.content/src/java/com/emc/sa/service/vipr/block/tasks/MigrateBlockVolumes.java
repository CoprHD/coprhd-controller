/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.Set;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.MigrationParam;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.exceptions.ServiceErrorException;

public class MigrateBlockVolumes extends WaitForTasks<VolumeRestRep> {
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
    protected Tasks<VolumeRestRep> doExecute() throws Exception {
        Tasks<VolumeRestRep> tasks = new Tasks<VolumeRestRep>(getClient().auth().getClient(), null,
                VolumeRestRep.class);

        for (URI volume : volumeIds) {
            MigrationParam param = new MigrationParam();
            param.setSrcStorageSystem(sourceStorageSystem);
            param.setTgtStorageSystem(targetStorageSystem);
            param.setVirtualPool(targetVirtualPoolId);
            param.setVolume(volume);

            try {
                tasks.getTasks().add(getClient().blockVolumes().migrate(param));
            } catch (ServiceErrorException ex) {
                logError(ex.getDetailedMessage());
            }
        }
        return tasks;
    }
}
