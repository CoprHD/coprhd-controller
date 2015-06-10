/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.SNAPSHOTS;

import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.DeactivateBlockSnapshot;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.vipr.client.Tasks;

@Service("RemoveBlockSnapshot")
public class RemoveBlockSnapshotService extends ViPRService {
    @Param(SNAPSHOTS)
    protected List<String> snapshotIds;

    @Override
    public void execute() {
        for (String snapshotId : snapshotIds) {
            Tasks<BlockSnapshotRestRep> task = execute(new DeactivateBlockSnapshot(snapshotId));
            addAffectedResources(task);
        }
    }
}
