/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.vipr.client.Tasks;

public class DeactivateBlockSnapshot extends WaitForTasks<BlockSnapshotRestRep> {
    private URI snapshotId;

    public DeactivateBlockSnapshot(String snapshotId) {
        this(uri(snapshotId));
    }

    public DeactivateBlockSnapshot(URI snapshotId) {
        super();
        this.snapshotId = snapshotId;
        provideDetailArgs(snapshotId);
    }

    @Override
    protected Tasks<BlockSnapshotRestRep> doExecute() throws Exception {
        return getClient().blockSnapshots().deactivate(snapshotId);
    }
}
