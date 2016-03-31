/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.CONSISTENCY_GROUP;
import static com.emc.sa.service.ServiceParams.SNAPSHOTS;
import static com.emc.sa.service.ServiceParams.STORAGE_TYPE;
import static com.emc.sa.service.ServiceParams.TYPE;

import java.net.URI;
import java.util.List;

import com.emc.sa.asset.providers.BlockProvider;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.RestoreBlockSnapshot;
import com.emc.sa.service.vipr.block.tasks.RestoreBlockSnapshotSession;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.client.Task;

@Service("RestoreBlockSnapshot")
public class RestoreBlockSnapshotService extends ViPRService {

    @Param(value = STORAGE_TYPE, required = false)
    protected String storageType;
    
    @Param(value = TYPE, required = false)
    protected String type;

    @Param(value = CONSISTENCY_GROUP, required = false)
    protected URI consistencyGroupId;

    @Param(SNAPSHOTS)
    protected List<String> snapshotIds;

    @Override
    public void execute() {
        for (String snapshotId : snapshotIds) {
            Task<? extends DataObjectRestRep> task;
            if (ConsistencyUtils.isVolumeStorageType(storageType)) {
                if (BlockProvider.SNAPSHOT_SESSION_TYPE_VALUE.equals(type)) {
                    task = execute(new RestoreBlockSnapshotSession(snapshotId));
                } else {
                    task = execute(new RestoreBlockSnapshot(snapshotId));
                }
            } else {
                if (BlockProvider.CG_SNAPSHOT_SESSION_TYPE_VALUE.equals(type)) {
                    task = ConsistencyUtils.restoreSnapshotSession(consistencyGroupId, uri(snapshotId));
                } else {
                    task = ConsistencyUtils.restoreSnapshot(consistencyGroupId, uri(snapshotId));   
                }                
            }
            addAffectedResource(task);
        }
    }
}
