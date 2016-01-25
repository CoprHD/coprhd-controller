/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.vipr.client.Tasks;

public class DeactivateBlockSnapshot extends WaitForTasks<BlockSnapshotRestRep> {
    private final URI snapshotId;
    private final VolumeDeleteTypeEnum type;

    public DeactivateBlockSnapshot(String snapshotId, VolumeDeleteTypeEnum type) {
        this(uri(snapshotId), type);
    }

    public DeactivateBlockSnapshot(URI snapshotId, VolumeDeleteTypeEnum type) {
        super();
        this.snapshotId = snapshotId;
        this.type = type;
        provideDetailArgs(snapshotId);
    }

    @Override
    protected Tasks<BlockSnapshotRestRep> doExecute() throws Exception {
        return getClient().blockSnapshots().deactivate(snapshotId, type);
    }
}
