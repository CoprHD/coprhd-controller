/*
 * Copyright (c) 2015 EMC Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_COPYMODE;
import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_COUNT;
import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_NAME;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.READ_ONLY;
import static com.emc.sa.service.ServiceParams.STORAGE_TYPE;
import static com.emc.sa.service.ServiceParams.TYPE;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.sa.asset.providers.BlockProvider;
import com.emc.sa.engine.ExecutionContext;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.CreateBlockSnapshot;
import com.emc.sa.service.vipr.block.tasks.CreateBlockSnapshotSession;
import com.emc.sa.service.vipr.block.tasks.DeactivateBlockSnapshot;
import com.emc.sa.service.vipr.block.tasks.DeactivateBlockSnapshotSession;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.db.client.model.uimodels.RetainedReplica;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

@Service("CreateBlockSnapshot")
public class CreateBlockSnapshotService extends ViPRService {

    @Param(PROJECT)
    protected URI project;
    
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
        
        // We disable recurring VMAX V3 snapshot in case when "Local Array Snapshot Type" is selected. With "Local Array Snapshot Type" enabled for VMAX V3, 
        // both snapshot sessions and snapshot are created. It brings some difficulties for snapshot rotation. So far we don't have single API to delete 
        // both snapshot session and snapshot in single shot. If we implement orchestration of those 2 calls at sasvc, we may introduce unnecessary complexities
        // for parameter preparation, error handling in the middle etc. So we would take out this special case and go back to it until a backend API is ready for
        // deletion both snapshot session and snapshot in single shot.
        if (isRetentionRequired()) {
            if (ConsistencyUtils.isVolumeStorageType(storageType)) {
                for (String volumeId : volumeIds) {
                    if(!BlockProvider.SNAPSHOT_SESSION_TYPE_VALUE.equals(type) && isSnapshotSessionSupportedForVolume(uri(volumeId))) {
                        ExecutionUtils.fail("failTask.CreateBlockSnapshot.localArraySnapshotTypeNotSupportedForScheduler.precheck", new Object[] {}, new Object[] {});
                    }
                }
            } else {
                for (String consistencyGroupId : volumeIds) {
                    if(!BlockProvider.CG_SNAPSHOT_SESSION_TYPE_VALUE.equals(type) && isSnapshotSessionSupportedForCG(uri(consistencyGroupId))) {
                        ExecutionUtils.fail("failTask.CreateBlockSnapshot.CGSnapshotTypeNotSupportedForScheduler.precheck", new Object[] {}, new Object[] {});
                    }
                }
            }
        }

        // Show alert in case of approaching 90% of the limit
        ExecutionContext context = ExecutionUtils.currentContext();
        long limit = context.getResourceLimit(Constants.RESOURCE_LIMIT_PROJECT_SNAPSHOTS);
        int numOfSnapshots = 0;
        if(BlockProvider.SNAPSHOT_SESSION_TYPE_VALUE.equals(type) || BlockProvider.CG_SNAPSHOT_SESSION_TYPE_VALUE.equals(type)) {
            numOfSnapshots = getClient().blockSnapshotSessions().countByProject(project);
        }  else {
            numOfSnapshots = getClient().blockSnapshots().countByProject(project);
        }
        if (numOfSnapshots  >= limit * Constants.RESOURCE_LIMIT_ALERT_RATE) {
            context.logWarn("alert.createSnapshot.exceedingResourceLimit", numOfSnapshots, limit);
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
            if(replica.getAssociatedReplicaIds() == null || replica.getAssociatedReplicaIds().isEmpty())
                continue;
            for (String obsoleteSnapshotId : replica.getAssociatedReplicaIds()) {
                info("Deactivating snapshot %s since it exceeds max number of snapshots allowed", obsoleteSnapshotId);
                
                if (ConsistencyUtils.isVolumeStorageType(storageType)) {
                    if (BlockProvider.SNAPSHOT_SESSION_TYPE_VALUE.equals(type)) {
                        BlockSnapshotSessionRestRep obsoloteCopy =  getClient().blockSnapshotSessions().get(uri(obsoleteSnapshotId));
                        info("Deactivating snapshot session %s", obsoloteCopy.getName());
                        execute(new DeactivateBlockSnapshotSession(uri(obsoleteSnapshotId)));
                    } else {
                        BlockObjectRestRep obsoleteCopy = BlockStorageUtils.getVolume(uri(obsoleteSnapshotId));
                        info("Deactivating snapshot %s", obsoleteCopy.getName());
                        execute(new DeactivateBlockSnapshot(uri(obsoleteSnapshotId), VolumeDeleteTypeEnum.FULL));
                    }
                } else {
                    if (BlockProvider.CG_SNAPSHOT_SESSION_TYPE_VALUE.equals(type)) {
                        BlockSnapshotSessionRestRep obsoloteCopy =  getClient().blockSnapshotSessions().get(uri(obsoleteSnapshotId));
                        info("Deactivating snapshot session %s", obsoloteCopy.getName());
                        ConsistencyUtils.removeSnapshotSession(uri(volumeOrCgId), uri(obsoleteSnapshotId));
                    } else {
                        BlockObjectRestRep obsoleteCopy = BlockStorageUtils.getVolume(uri(obsoleteSnapshotId));
                        info("Deactivating snapshot %s", obsoleteCopy.getName());
                        ConsistencyUtils.removeSnapshot(uri(volumeOrCgId), uri(obsoleteSnapshotId));
                    }
                }
            }
            getModelClient().delete(replica);
        } 
    }
    
    private boolean isSnapshotSessionSupportedForVolume(URI volumeId) {
        VolumeRestRep volume = getClient().blockVolumes().get(volumeId);
        return volume.getSupportsSnapshotSessions();
    }
    
    private boolean isSnapshotSessionSupportedForCG(URI cgId) {
        BlockConsistencyGroupRestRep cg = getClient().blockConsistencyGroups().get(cgId);
        return cg.getSupportsSnapshotSessions();
    }
}
