/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.VolumeRestRep;

public class GetActiveFullCopiesForVolume extends ViPRExecutionTask<List<VolumeRestRep>> {
    private URI volumeId;

    public GetActiveFullCopiesForVolume(String volumeId) {
        this(uri(volumeId));
    }

    public GetActiveFullCopiesForVolume(URI volumeId) {
        this.volumeId = volumeId;
        provideDetailArgs(volumeId);
    }

    @Override
    public List<VolumeRestRep> executeTask() throws Exception {
        return getClient().blockVolumes().getFullCopies(volumeId);
    }
}
