/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.consistency.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.vipr.client.Task;

public class RestoreConsistencyGroupSnapshotSession extends
        WaitForTask<BlockConsistencyGroupRestRep> {
    private URI consistencyGroupId;
    private URI snapshotSessionId;

    public RestoreConsistencyGroupSnapshotSession(String consistencyGroupId, String snapshotSessionId) {
        this(uri(consistencyGroupId), uri(snapshotSessionId));
    }
    
    public RestoreConsistencyGroupSnapshotSession(URI consistencyGroupId, URI snapshotSessionId) {
        this.consistencyGroupId = consistencyGroupId;
        this.snapshotSessionId = snapshotSessionId;
        provideDetailArgs(consistencyGroupId, snapshotSessionId);
    }

    @Override
    protected Task<BlockConsistencyGroupRestRep> doExecute() throws Exception {
        return getClient().blockConsistencyGroups().restoreSnapshotSession(consistencyGroupId, snapshotSessionId);
    }
}
