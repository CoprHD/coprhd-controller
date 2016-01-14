/*
 * Copyright (c) 2015 EMC Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT;
import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_COPYMODE;
import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_COUNT;
import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_NAME;
import static com.emc.sa.service.ServiceParams.SNAPSHOT_SESSION;
import static com.emc.sa.service.ServiceParams.STORAGE_TYPE;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.util.List;

import com.emc.sa.asset.providers.BlockProvider;
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
    
    @Param(value = LINKED_SNAPSHOT_COPYMODE, required = false)
    protected String linkedSnapshotCopyMode;

    @Override
    public void precheck() {
        if (ConsistencyUtils.isVolumeStorageType(storageType)) {                                    
            if (linkedSnapshotName != null && !linkedSnapshotName.isEmpty()) {
                // Can not relink an existing snapshot and link a new snapshot at the same time
                if (existingLinkedSnapshotIds != null && !existingLinkedSnapshotIds.isEmpty()) {
                    ExecutionUtils.fail("failTask.LinkBlockSnapshot.linkNewAndExistingSnapshot.precheck", new Object[] {}, new Object[] {});
                }                
                // If trying to create a new Snapshot Session and the optional linkedSnapshotName 
                // is populated, make sure that linkedSnapshotCount > 0.                      
                if (linkedSnapshotCount == null || linkedSnapshotCount.intValue() <= 0) {
                    ExecutionUtils.fail("failTask.CreateBlockSnapshot.linkedSnapshotCount.precheck", new Object[] {}, new Object[] {});
                }
                // Ensure that copy mode is selected            
                if (linkedSnapshotCopyMode == null
                        || !(BlockProvider.LINKED_SNAPSHOT_COPYMODE_VALUE.equals(linkedSnapshotCopyMode)
                                || BlockProvider.LINKED_SNAPSHOT_NOCOPYMODE_VALUE.equals(linkedSnapshotCopyMode))) {
                    ExecutionUtils.fail("failTask.CreateBlockSnapshot.linkedSnapshotCopyMode.precheck", new Object[] {}, new Object[] {});
                }
            }            
        }
    }

    @Override
    public void execute() {        
        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            for (String snapshotSessionId : snapshotSessionIds) {
                Task<? extends DataObjectRestRep> task;
                task = execute(new LinkBlockSnapshot(snapshotSessionId, existingLinkedSnapshotIds, linkedSnapshotName, linkedSnapshotCount, linkedSnapshotCopyMode));
                addAffectedResource(task);
            }
        } else {
            // CG not supported for now
        }
    }
}
