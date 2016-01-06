/*
 * Copyright (c) 2015 EMC Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT;
import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_COUNT;
import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_NAME;
import static com.emc.sa.service.ServiceParams.SNAPSHOT_SESSION;
import static com.emc.sa.service.ServiceParams.STORAGE_TYPE;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.LinkBlockSnapshot;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.client.Task;

@Service("LinkBlockSnapshot")
public class LinkBlockSnapshotService extends ViPRService {

    @Param(value = STORAGE_TYPE, required = false)
    protected String storageType;

    @Param(VOLUMES)
    protected List<String> volumeIds;
    
    @Param(SNAPSHOT_SESSION)
    protected List<String> snapshotSessionIds;
    
    @Param(value = LINKED_SNAPSHOT, required = false)
    protected List<String> existingLinkedSnapshotIds;
    
    @Param(value = LINKED_SNAPSHOT_NAME, required = false)
    protected String linkedSnapshotName;
    
    @Param(value = LINKED_SNAPSHOT_COUNT, required = false)
    protected Integer linkedSnapshotCount;

    @Override
    public void precheck() {
        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            // If trying to create a new Snapshot Session and the optional linkedSnapshotName 
            // is populated, make sure that linkedSnapshotCount > 0.                      
            if (linkedSnapshotName != null && !linkedSnapshotName.isEmpty()) {
                if (linkedSnapshotCount == null || linkedSnapshotCount.intValue() <= 0) {
                    ExecutionUtils.fail("failTask.CreateBlockSnapshot.linkedSnapshotCount.precheck", new Object[] {}, new Object[] {});
                }
            }            
        }
    }

    @Override
    public void execute() {        
        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            for (String snapshotSessionId : snapshotSessionIds) {
                Task<? extends DataObjectRestRep> task;
                task = execute(new LinkBlockSnapshot(snapshotSessionId, existingLinkedSnapshotIds, linkedSnapshotName, linkedSnapshotCount, "nocopy"));
                addAffectedResource(task);
            }
        } else {
            // CG not supported for now
        }
    }
}
