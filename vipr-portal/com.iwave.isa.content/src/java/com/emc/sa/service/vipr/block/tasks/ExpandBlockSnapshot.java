/*
 * Copyright (c) 2012-2018 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.BlockSnapshotExpandParam;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.vipr.client.Task;

public class ExpandBlockSnapshot extends WaitForTask<BlockSnapshotRestRep> {
    private URI snapshotId;
    private String newSize;

    public ExpandBlockSnapshot(String snapshotId, String newSize) {
        this(uri(snapshotId), newSize);
    }

    public ExpandBlockSnapshot(URI snapshotId, String newSize) {
        super();
        this.snapshotId = snapshotId;
        this.newSize = newSize;
        provideDetailArgs(snapshotId);
    }

    @Override
    protected Task<BlockSnapshotRestRep> doExecute() throws Exception {
        return getClient().blockSnapshots().expand(snapshotId, new BlockSnapshotExpandParam(newSize));
    }
}
