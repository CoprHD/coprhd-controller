/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.BlockMirrorRestRep;

public class GetActiveContinuousCopiesForVolume extends ViPRExecutionTask<List<BlockMirrorRestRep>> {
    private URI volumeId;

    public GetActiveContinuousCopiesForVolume(String volumeId) {
        this(uri(volumeId));
    }

    public GetActiveContinuousCopiesForVolume(URI volumeId) {
        this.volumeId = volumeId;
        provideDetailArgs(volumeId);
    }

    @Override
    public List<BlockMirrorRestRep> executeTask() throws Exception {
        return getClient().blockVolumes().getContinuousCopies(volumeId);
    }
}
