/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.vipr.client.Task;

public class RestoreBlockSnapshotSession extends WaitForTask<BlockSnapshotSessionRestRep> {
    private URI snapshotId;

    public RestoreBlockSnapshotSession(String snapshotId) {
        this(uri(snapshotId));
    }

    public RestoreBlockSnapshotSession(URI snapshotId) {
        super();
        this.snapshotId = snapshotId;

        provideDetailArgs(snapshotId);
    }

    @Override
    protected Task<BlockSnapshotSessionRestRep> doExecute() throws Exception {
        return getClient().blockSnapshotSessions().restore(snapshotId);
    }
}
