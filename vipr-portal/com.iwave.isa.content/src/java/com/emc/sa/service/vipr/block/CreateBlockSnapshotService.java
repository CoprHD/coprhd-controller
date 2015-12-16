/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.READ_ONLY;
import static com.emc.sa.service.ServiceParams.SNAPSHOTS;
import static com.emc.sa.service.ServiceParams.STORAGE_TYPE;
import static com.emc.sa.service.ServiceParams.TYPE;
import static com.emc.sa.service.ServiceParams.VOLUME;
import static com.emc.sa.service.ServiceParams.VOLUMES;
import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_NAME;
import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_COUNT;

import java.util.ArrayList;
import java.util.List;

import com.emc.sa.asset.providers.BlockProvider;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.CreateBlockSnapshot;
import com.emc.sa.service.vipr.block.tasks.CreateBlockSnapshotSession;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.vipr.client.Tasks;

@Service("CreateBlockSnapshot")
public class CreateBlockSnapshotService extends ViPRService {

    @Param(value = STORAGE_TYPE, required = false)
    protected String storageType;

    @Param(VOLUMES)
    protected List<String> volumeIds;

    @Param(NAME)
    protected String nameParam;

    @Param(value = TYPE, required = true)
    protected String type;

    @Param(value = READ_ONLY, required = false)
    protected Boolean readOnly;
    
    @Param(value = LINKED_SNAPSHOT_NAME, required = false)
    public String linkedSnapshotName;
    
    @Param(value = LINKED_SNAPSHOT_COUNT, required = false)
    public Integer linkedSnapshotCount;

    private List<BlockObjectRestRep> volumes;

    @Override
    public void precheck() {
        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            volumes = new ArrayList<>();
            volumes = BlockStorageUtils.getBlockResources(uris(volumeIds));
            // If trying to create a Snapshot Session and the optional linkedSnapshotName 
            // is populated, make sure that linkedSnapshotCount > 0.
            if (type.equals(BlockProvider.SESSION_SNAPSHOT_TYPE_VALUE)) {               
                if (linkedSnapshotName != null && !linkedSnapshotName.isEmpty()) {
                    if (linkedSnapshotCount == null || linkedSnapshotCount.intValue() <= 0) {
                        ExecutionUtils.fail("failTask.CreateBlockSnapshot.linkedSnapshotCount.precheck", new Object[] {}, new Object[] {});
                    }
                }
            }
        }
    }

    @Override
    public void execute() {
        Tasks<? extends DataObjectRestRep> tasks;
        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            for (BlockObjectRestRep volume : volumes) {
                if (BlockProvider.SESSION_SNAPSHOT_TYPE_VALUE.equals(type)) {
                    tasks = execute(new CreateBlockSnapshotSession(volume.getId(), nameParam, 
                                                                    linkedSnapshotName, linkedSnapshotCount, "nocopy"));
                } else {
                    tasks = execute(new CreateBlockSnapshot(volume.getId(), type, nameParam, readOnly));
                }
                addAffectedResources(tasks);
            }
        } else {
            for (String consistencyGroupId : volumeIds) {
                tasks = ConsistencyUtils.createSnapshot(uri(consistencyGroupId), nameParam, readOnly);
                addAffectedResources(tasks);
            }
        }
    }
}
