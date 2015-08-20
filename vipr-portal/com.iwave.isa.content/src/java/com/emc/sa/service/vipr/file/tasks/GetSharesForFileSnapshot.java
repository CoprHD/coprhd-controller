/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.SmbShareResponse;

public class GetSharesForFileSnapshot extends ViPRExecutionTask<List<SmbShareResponse>> {
    private final URI snapshotId;

    public GetSharesForFileSnapshot(String snapshotId) {
        this(uri(snapshotId));
    }

    public GetSharesForFileSnapshot(URI snapshotId) {
        this.snapshotId = snapshotId;
        provideDetailArgs(snapshotId);
    }

    @Override
    public List<SmbShareResponse> executeTask() throws Exception {
        return getClient().fileSnapshots().getShares(snapshotId);
    }
}
