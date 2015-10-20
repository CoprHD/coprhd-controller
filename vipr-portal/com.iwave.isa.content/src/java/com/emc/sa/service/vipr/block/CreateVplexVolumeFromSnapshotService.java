/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.SNAPSHOTS;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.tasks.CreateVplexVolumeFromSnapshot;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.vipr.client.Task;

@Service("CreateVplexVolumeFromSnapshot")
public class CreateVplexVolumeFromSnapshotService extends ViPRService {

    @Param(value = SNAPSHOTS)
    protected List<String> snapshotIds;

    @Override
    public void execute() throws Exception {
        for (URI snapshotId : uris(snapshotIds)) {
            Task<BlockSnapshotRestRep> task = execute(new CreateVplexVolumeFromSnapshot(snapshotId));
            URI volume = task.getResourceId();
            addAffectedResource(volume);
        }
    }
}
