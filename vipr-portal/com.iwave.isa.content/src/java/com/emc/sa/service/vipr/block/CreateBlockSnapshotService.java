/*
 * Copyright (c) 2015 EMC Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_COPYMODE;
import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_COUNT;
import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_NAME;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.READ_ONLY;
import static com.emc.sa.service.ServiceParams.STORAGE_TYPE;
import static com.emc.sa.service.ServiceParams.TYPE;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.util.ArrayList;
import java.util.List;

import com.emc.sa.asset.providers.BlockProvider;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.CreateBlockSnapshot;
import com.emc.sa.service.vipr.block.tasks.CreateBlockSnapshotSession;
import com.emc.sa.service.vipr.block.tasks.DeactivateBlockSnapshot;
import com.emc.sa.service.vipr.block.tasks.DeactivateBlockSnapshotSession;
import com.emc.storageos.db.client.model.uimodels.RetainedReplica;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.vipr.client.Tasks;

@Service("CreateBlockSnapshot")
public class CreateBlockSnapshotService extends ViPRService {

    @Param(value = STORAGE_TYPE, required = false)
    protected String storageType;

    @Param(VOLUMES)
    protected List<String> volumeIds;

    @Param(NAME)
    protected String nameParam;

    @Param(value = TYPE, required = false)
    protected String type;

    @Param(value = READ_ONLY, required = false)
    protected Boolean readOnly;
    
    @Param(value = LINKED_SNAPSHOT_NAME, required = false)
    protected String linkedSnapshotName;
    
    @Param(value = LINKED_SNAPSHOT_COUNT, required = false)
    protected Integer linkedSnapshotCount;
        
    @Param(value = LINKED_SNAPSHOT_COPYMODE, required = false)
    protected String linkedSnapshotCopyMode;

    private List<BlockObjectRestRep> volumes;
    
    
    @Override
    public void precheck() {
        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            volumes = new ArrayList<>();
            volumes = BlockStorageUtils.getBlockResources(uris(volumeIds));
        }
            
        if (BlockProvider.SNAPSHOT_SESSION_TYPE_VALUE.equals(type)
                || BlockProvider.CG_SNAPSHOT_SESSION_TYPE_VALUE.equals(type)) {               
            if (linkedSnapshotName != null && !linkedSnapshotName.isEmpty()) {
                // If trying to create a Snapshot Session and the optional linkedSnapshotName 
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
    	Tasks<? extends DataObjectRestRep> tasks = null;
        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            for (BlockObjectRestRep volume : volumes) {
                checkAndPurgeObsoleteSnapshots(volume.getId().toString());
                
                if (BlockProvider.SNAPSHOT_SESSION_TYPE_VALUE.equals(type)) {
                    tasks = execute(new CreateBlockSnapshotSession(volume.getId(), nameParam, 
                                                                    linkedSnapshotName, linkedSnapshotCount, linkedSnapshotCopyMode));
                } else {
                    tasks = execute(new CreateBlockSnapshot(volume.getId(), type, nameParam, readOnly));
                    
                }
                addAffectedResources(tasks);
                addRetainedReplicas(volume.getId(), tasks.getTasks());
            }
        } else {
            for (String consistencyGroupId : volumeIds) {
                checkAndPurgeObsoleteSnapshots(consistencyGroupId);
                
                if (BlockProvider.CG_SNAPSHOT_SESSION_TYPE_VALUE.equals(type)) {
                    tasks = ConsistencyUtils.createSnapshotSession(uri(consistencyGroupId), nameParam, 
                                                                    linkedSnapshotName, linkedSnapshotCount, linkedSnapshotCopyMode);
                } else {
                    tasks = ConsistencyUtils.createSnapshot(uri(consistencyGroupId), nameParam, readOnly);
                }
                addAffectedResources(tasks);
                addRetainedReplicas(uri(consistencyGroupId), tasks.getTasks());
            }
        }
    }

    /**
     * Check retention policy and delete obsolete snapshots if necessary
     * 
     * @param volumeOrCgId - volume id or consistency group id 
     */
    private void checkAndPurgeObsoleteSnapshots(String volumeOrCgId) {
        if (!isRetentionRequired()) {
            return;
        }
        List<RetainedReplica> replicas = findObsoleteReplica(volumeOrCgId);
        for (RetainedReplica replica : replicas) {
            for (String obsoleteSnapshotId : replica.getAssociatedReplicaIds()) {
                info("Deactivating snapshot %s since it exceeds max number of snapshots allowed", obsoleteSnapshotId);
                
                if (ConsistencyUtils.isVolumeStorageType(storageType)) {
                    if (BlockProvider.SNAPSHOT_SESSION_TYPE_VALUE.equals(type)) {
                        execute(new DeactivateBlockSnapshotSession(uri(obsoleteSnapshotId)));
                    } else {
                        execute(new DeactivateBlockSnapshot(uri(obsoleteSnapshotId), VolumeDeleteTypeEnum.FULL));
                    }
                } else {
                    if (BlockProvider.CG_SNAPSHOT_SESSION_TYPE_VALUE.equals(type)) {
                        ConsistencyUtils.removeSnapshotSession(uri(volumeOrCgId), uri(obsoleteSnapshotId));
                    } else {
                        ConsistencyUtils.removeSnapshot(uri(volumeOrCgId), uri(obsoleteSnapshotId));
                    }
                }
            }
            getModelClient().delete(replica);
        } 
    }
}
