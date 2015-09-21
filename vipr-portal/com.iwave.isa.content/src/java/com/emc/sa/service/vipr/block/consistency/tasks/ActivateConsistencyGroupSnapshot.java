/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.consistency.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.vipr.client.Task;

public class ActivateConsistencyGroupSnapshot extends
        WaitForTask<BlockConsistencyGroupRestRep> {

    private URI consistencyGroup;

    public ActivateConsistencyGroupSnapshot(URI consistencyGroup) {
        this.consistencyGroup = consistencyGroup;
    }

    @Override
    protected Task<BlockConsistencyGroupRestRep> doExecute() throws Exception {
        NamedRelatedResourceRep item = getClient().blockConsistencyGroups().getSnapshots(consistencyGroup).get(0);
        return getClient().blockConsistencyGroups().activateSnapshot(consistencyGroup, item.getId());
    }
}
