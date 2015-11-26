
/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.consistency.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.vipr.client.Task;

public class ResynchronizeConsistencyGroupSnapshot extends
        WaitForTask<BlockConsistencyGroupRestRep> {

    private URI consistencyGroup;
    private URI snapshot;

    public ResynchronizeConsistencyGroupSnapshot(URI consistencyGroup, URI snapshot) {
        this.consistencyGroup = consistencyGroup;
        this.snapshot = snapshot;
    }

    @Override
    protected Task<BlockConsistencyGroupRestRep> doExecute() throws Exception {
        return getClient().blockConsistencyGroups().resynchronizeSnapshot(consistencyGroup, snapshot);
    }
}
