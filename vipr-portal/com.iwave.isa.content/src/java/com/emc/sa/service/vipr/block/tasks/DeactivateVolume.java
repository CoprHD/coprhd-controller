/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Task;

public class DeactivateVolume extends WaitForTask<VolumeRestRep> {
    private URI volumeId;
    private VolumeDeleteTypeEnum type;

    public DeactivateVolume(String volumeId, VolumeDeleteTypeEnum type) {
        this(uri(volumeId), type);
    }

    public DeactivateVolume(URI volumeId, VolumeDeleteTypeEnum type) {
        super();
        this.volumeId = volumeId;
        this.type = type;
        provideDetailArgs(volumeId, type);
    }

    @Override
    protected Task<VolumeRestRep> doExecute() throws Exception {
        return getClient().blockVolumes().deactivate(volumeId, type);
    }
}
