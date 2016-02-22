/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;

public class GetBlockSnapshotSession extends ViPRExecutionTask<BlockSnapshotSessionRestRep> {
    private final URI id;

    public GetBlockSnapshotSession(URI id) {
        this.id = id;
        provideDetailArgs(id);
    }

    @Override
    public BlockSnapshotSessionRestRep executeTask() throws Exception {
        return getClient().blockSnapshotSessions().get(id);
    }
}