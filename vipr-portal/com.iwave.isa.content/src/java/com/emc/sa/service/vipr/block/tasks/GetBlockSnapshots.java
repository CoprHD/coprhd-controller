/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.BlockSnapshotRestRep;

public class GetBlockSnapshots extends ViPRExecutionTask<List<BlockSnapshotRestRep>> {

    private List<URI> resourceIds;

    public GetBlockSnapshots(List<URI> resourceIds) {
        this.resourceIds = resourceIds;
        provideDetailArgs(resourceIds);
    }

    @Override
    public List<BlockSnapshotRestRep> executeTask() throws Exception {
        return getClient().blockSnapshots().getByIds(resourceIds);
    }

}