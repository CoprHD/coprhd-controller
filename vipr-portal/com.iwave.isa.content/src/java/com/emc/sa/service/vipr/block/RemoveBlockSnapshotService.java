/*
 * Copyright (c) 2015 EMC Software LLC
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
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.DeactivateBlockSnapshot;
import com.emc.sa.service.vipr.block.tasks.DeactivateBlockSnapshotSession;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.vipr.client.Tasks;

@Service("RemoveBlockSnapshot")
public class RemoveBlockSnapshotService extends ViPRService {

    @Param(value = STORAGE_TYPE, required = false)
    protected String storageType;

    @Param(value = TYPE, required = false)
    protected String type;

    @Param(value = CONSISTENCY_GROUP, required = false)
    protected URI consistencyGroupId;

    @Param(SNAPSHOTS)
    protected List<String> snapshotIds;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        if (!ConsistencyUtils.isVolumeStorageType(storageType)) {
            if (consistencyGroupId == null) {
                ExecutionUtils.fail("failTask.ConsistencyGroup.noConsistencyGroup", consistencyGroupId);
            }
        }
    }

    @Override
    public void execute() {
        for (String snapshotId : snapshotIds) {
            Tasks<? extends DataObjectRestRep> tasks;
            if (ConsistencyUtils.isVolumeStorageType(storageType)) {
                if (BlockProvider.SESSION_SNAPSHOT_TYPE_VALUE.equals(type)) {
                    tasks = execute(new DeactivateBlockSnapshotSession(snapshotId));
                } else {
                    tasks = execute(new DeactivateBlockSnapshot(snapshotId, VolumeDeleteTypeEnum.FULL));
                }
            } else {
                tasks = ConsistencyUtils.removeSnapshot(consistencyGroupId, uri(snapshotId));
            }
            addAffectedResources(tasks);
        }
    }
}
