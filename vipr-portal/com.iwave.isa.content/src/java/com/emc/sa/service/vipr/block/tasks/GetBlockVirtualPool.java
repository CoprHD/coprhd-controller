/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;

public class GetBlockVirtualPool extends ViPRExecutionTask<BlockVirtualPoolRestRep> {
    private URI vpoolId;

    public GetBlockVirtualPool(URI vpoolId) {
        this.vpoolId = vpoolId;
        provideDetailArgs(vpoolId);
    }

    @Override
    public BlockVirtualPoolRestRep executeTask() throws Exception {
        return getClient().blockVpools().get(vpoolId);
    }
}