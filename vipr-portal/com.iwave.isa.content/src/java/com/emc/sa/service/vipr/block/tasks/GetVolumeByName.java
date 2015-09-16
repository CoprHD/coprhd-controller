/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.VolumeRestRep;

public class GetVolumeByName extends ViPRExecutionTask<List<VolumeRestRep>> {
    private String volumeName;

    public GetVolumeByName(String volumeName) {
        this.volumeName = volumeName;
        provideDetailArgs(volumeName);
    }

    @Override
    public List<VolumeRestRep> executeTask() throws Exception {
        return getClient().blockVolumes().findByName(volumeName);
    }
}
