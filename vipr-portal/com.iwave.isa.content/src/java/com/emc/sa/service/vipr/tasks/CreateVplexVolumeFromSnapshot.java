/*
 * Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import java.net.URI;

import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.vipr.client.Task;

public class CreateVplexVolumeFromSnapshot extends WaitForTask<BlockSnapshotRestRep> {
    private final URI snapshotId;

    public CreateVplexVolumeFromSnapshot(URI snapshotId) {
        this.snapshotId = snapshotId;
        provideDetailArgs(snapshotId);
    }

    @Override
    protected Task<BlockSnapshotRestRep> doExecute() throws Exception {
        return getClient().blockSnapshots().expose(this.snapshotId);
    }

}