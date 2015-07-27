/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.COUNT;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.SNAPSHOTS;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.Task;

@Service("CreateSnapshotFullCopy")
public class CreateSnapshotFullCopyService extends ViPRService {

    @Param(SNAPSHOTS)
    protected URI snapshotId;

    @Param(NAME)
    protected String name;

    @Param(COUNT)
    protected Integer count;
    
    @Override
    public void execute() throws Exception {
        Tasks<BlockSnapshotRestRep> copyTasks = BlockStorageUtils.createSnapshotFullCopy(snapshotId, name, count);
        for (Task<BlockSnapshotRestRep> copyTask : copyTasks.getTasks()) {
            logInfo("create.snap.full.copy.service", copyTask.getResource().getName(), copyTask.getResource().getId());
        }
    }

}
