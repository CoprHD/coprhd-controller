/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.vipr.client.Task;

public class StartBlockSnapshot extends WaitForTask<BlockSnapshotRestRep> {
    private URI snapshotId;

    public StartBlockSnapshot(String snapshotId) {
        this(uri(snapshotId));
    }

    public StartBlockSnapshot(URI snapshotId) {
        super();
        this.snapshotId = snapshotId;
        provideDetailArgs(snapshotId);
    }

    @Override
    protected Task<BlockSnapshotRestRep> doExecute() throws Exception {
        return getClient().blockSnapshots().start(snapshotId);
    }
}
