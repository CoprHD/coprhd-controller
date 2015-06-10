/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.VolumeRestRep;

public class GetBlockVolume extends ViPRExecutionTask<VolumeRestRep> {
    private URI volumeId;

    public GetBlockVolume(String volumeId) {
        this(uri(volumeId));
    }

    public GetBlockVolume(URI volumeId) {
        this.volumeId = volumeId;
        provideDetailArgs(volumeId);
    }

    @Override
    public VolumeRestRep executeTask() throws Exception {
        return getClient().blockVolumes().get(volumeId);
    }
}