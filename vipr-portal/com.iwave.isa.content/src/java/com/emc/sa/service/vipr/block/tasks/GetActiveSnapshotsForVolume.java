/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.BlockSnapshotRestRep;

public class GetActiveSnapshotsForVolume extends ViPRExecutionTask<List<BlockSnapshotRestRep>> {
    private URI volumeId;

    public GetActiveSnapshotsForVolume(String volumeId) {
        this(uri(volumeId));
    }

    public GetActiveSnapshotsForVolume(URI volumeId) {
        this.volumeId = volumeId;
        provideDetailArgs(volumeId);
    }

    @Override
    public List<BlockSnapshotRestRep> executeTask() throws Exception {
        return getClient().blockSnapshots().getByVolume(volumeId);
    }
}
