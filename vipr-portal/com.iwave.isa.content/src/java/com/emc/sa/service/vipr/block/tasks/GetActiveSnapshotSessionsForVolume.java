/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;

public class GetActiveSnapshotSessionsForVolume extends ViPRExecutionTask<List<BlockSnapshotSessionRestRep>> {
    private final URI volumeId;

    public GetActiveSnapshotSessionsForVolume(String volumeId) {
        this(uri(volumeId));
    }

    public GetActiveSnapshotSessionsForVolume(URI volumeId) {
        this.volumeId = volumeId;
        provideDetailArgs(volumeId);
    }

    @Override
    public List<BlockSnapshotSessionRestRep> executeTask() throws Exception {
        return getClient().blockSnapshotSessions().getByVolume(volumeId);
    }
}
