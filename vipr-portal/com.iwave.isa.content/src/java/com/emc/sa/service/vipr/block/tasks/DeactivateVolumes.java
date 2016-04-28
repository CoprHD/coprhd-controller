/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

public class DeactivateVolumes extends WaitForTasks<VolumeRestRep> {

    private List<URI> volumeIds;
    private VolumeDeleteTypeEnum type;

    public DeactivateVolumes(List<URI> volumeIds, VolumeDeleteTypeEnum type) {
        super();
        this.volumeIds = volumeIds;
        this.type = type;
        provideDetailArgs(volumeIds, type);
    }

    @Override
    protected Tasks<VolumeRestRep> doExecute() throws Exception {
        return getClient().blockVolumes().deactivate(volumeIds, type);
    }
}
