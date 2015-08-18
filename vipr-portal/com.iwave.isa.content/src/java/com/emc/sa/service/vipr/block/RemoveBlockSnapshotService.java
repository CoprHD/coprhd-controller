/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.CONSISTENCY_GROUP;
import static com.emc.sa.service.ServiceParams.SNAPSHOTS;
import static com.emc.sa.service.ServiceParams.STORAGE_TYPE;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.DeactivateBlockSnapshot;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.client.Tasks;

@Service("RemoveBlockSnapshot")
public class RemoveBlockSnapshotService extends ViPRService {

    @Param(STORAGE_TYPE)
    protected String storageType;

    @Param(CONSISTENCY_GROUP)
    protected URI consistencyGroupId;

    @Param(SNAPSHOTS)
    protected List<String> snapshotIds;

    @Override
    public void execute() {
        for (String snapshotId : snapshotIds) {
            Tasks<? extends DataObjectRestRep> tasks;
            if ("volume".equals(storageType)) {
                tasks = execute(new DeactivateBlockSnapshot(snapshotId));
            } else {
                tasks = ConsistencyUtils.removeSnapshot(consistencyGroupId, uri(snapshotId));
            }
            addAffectedResources(tasks);
        }
    }
}
