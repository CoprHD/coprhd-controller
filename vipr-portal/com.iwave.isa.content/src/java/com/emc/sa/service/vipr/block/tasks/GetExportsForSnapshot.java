/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.export.ITLRestRep;

public class GetExportsForSnapshot extends ViPRExecutionTask<List<ITLRestRep>> {

    private URI snapshotId;

    public GetExportsForSnapshot(String snapshotId) {
        this(uri(snapshotId));
    }

    public GetExportsForSnapshot(URI snapshotId) {
        this.snapshotId = snapshotId;
        provideDetailArgs(snapshotId);
    }

    @Override
    public List<ITLRestRep> executeTask() throws Exception {
        return getClient().blockSnapshots().listExports(snapshotId);
    }
}
