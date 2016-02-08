/*
 * Copyright (c) 2012-2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.vipr.client.Task;

public class ResynchronizeBlockSnapshot extends WaitForTask<BlockSnapshotRestRep> {
    private URI blockSnapshotId;

    public ResynchronizeBlockSnapshot(URI blockSnapshotId) {
        this.blockSnapshotId = blockSnapshotId;
        provideDetailArgs(blockSnapshotId);
    }

    @Override
    protected Task<BlockSnapshotRestRep> doExecute() throws Exception {
        return getClient().blockSnapshots().resynchronizeBlockSnapshot(blockSnapshotId);
    }
}
