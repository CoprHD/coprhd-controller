/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.consistency.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.BlockConsistencyGroupSnapshotCreate;
import com.emc.vipr.client.Tasks;

public class CreateConsistencyGroupSnapshot extends
        WaitForTasks<BlockConsistencyGroupRestRep> {

    private final URI consistencyGroupId;
    private final String name;
    private final Boolean readOnly;

    public CreateConsistencyGroupSnapshot(URI consistencyGroupId, String name, Boolean readOnly) {
        this.consistencyGroupId = consistencyGroupId;
        this.name = name;
        this.readOnly = readOnly;
    }

    @Override
    protected Tasks<BlockConsistencyGroupRestRep> doExecute() throws Exception {

        BlockConsistencyGroupSnapshotCreate param = new BlockConsistencyGroupSnapshotCreate();
        param.setName(name);

        if (readOnly != null) {
            param.setReadOnly(readOnly);
        }

        return getClient().blockConsistencyGroups().createSnapshot(
                consistencyGroupId, param);
    }
}
