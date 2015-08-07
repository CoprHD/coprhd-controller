/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.BlockSnapshotRestRep;

public class GetBlockSnapshot extends ViPRExecutionTask<BlockSnapshotRestRep> {
    private URI snapshotId;

    public GetBlockSnapshot(String snapshotId) {
        this(uri(snapshotId));
    }

    public GetBlockSnapshot(URI snapshotId) {
        this.snapshotId = snapshotId;
        provideDetailArgs(snapshotId);
    }

    @Override
    public BlockSnapshotRestRep executeTask() throws Exception {
        return getClient().blockSnapshots().get(snapshotId);
    }
}