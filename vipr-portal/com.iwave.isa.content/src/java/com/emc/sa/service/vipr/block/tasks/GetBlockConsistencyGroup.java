/*
 * Copyright 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.vipr.client.ViPRCoreClient;

public class GetBlockConsistencyGroup extends ViPRExecutionTask<BlockConsistencyGroupRestRep> {

    private final URI resourceId;

    public GetBlockConsistencyGroup(String resourceId) {
        this(uri(resourceId));
    }

    public GetBlockConsistencyGroup(URI resourceId) {
        this.resourceId = resourceId;
        provideDetailArgs(resourceId);
    }

    @Override
    public BlockConsistencyGroupRestRep executeTask() throws Exception {
        ViPRCoreClient client = getClient();
        BlockConsistencyGroupRestRep cg = client.blockConsistencyGroups().get(resourceId);
        if (cg != null) {
            return cg;
        }
        throw stateException("GetBlockConsistencyGroup.illegalState.notFound", resourceId);
    }

}