/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.vipr.client.Task;

public class RestoreBlockSnapshot extends WaitForTask<BlockSnapshotRestRep> {
    private URI snapshotId;

    public RestoreBlockSnapshot(String snapshotId) {
        this(uri(snapshotId));
    }

    public RestoreBlockSnapshot(URI snapshotId) {
        super();
        this.snapshotId = snapshotId;
        
        provideDetailArgs(snapshotId);
    }

    @Override
    protected Task<BlockSnapshotRestRep> doExecute() throws Exception {
        return getClient().blockSnapshots().restore(snapshotId);
    }
}
