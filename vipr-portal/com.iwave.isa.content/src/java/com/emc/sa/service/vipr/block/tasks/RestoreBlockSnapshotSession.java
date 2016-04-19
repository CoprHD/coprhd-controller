/*
 * Copyright (c) 2016 EMC Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.vipr.client.Task;

public class RestoreBlockSnapshotSession extends WaitForTask<BlockSnapshotSessionRestRep> {
    private URI snapshotSessionId;

    public RestoreBlockSnapshotSession(String snapshotSessionId) {
        this(uri(snapshotSessionId));
    }

    public RestoreBlockSnapshotSession(URI snapshotSessionId) {
        super();
        this.snapshotSessionId = snapshotSessionId;

        provideDetailArgs(snapshotSessionId);
    }

    @Override
    protected Task<BlockSnapshotSessionRestRep> doExecute() throws Exception {
        return getClient().blockSnapshotSessions().restore(snapshotSessionId);
    }
}
