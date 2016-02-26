/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT;
import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_COPYMODE;
import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_COUNT;
import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_NAME;

import java.net.URI;
import java.util.List;

import com.emc.sa.asset.providers.BlockProvider;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.application.tasks.LinkSnapshotSessionForApplication;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.vipr.client.Tasks;

@Service("LinkSnapshotOfApplication")
public class LinkSnapshotOfApplicationService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.APPLICATION_COPY_SETS)
    protected String copySet;

    @Param(ServiceParams.APPLICATION_SUB_GROUP)
    protected List<String> subGroups;

    @Param(value = LINKED_SNAPSHOT, required = false)
    protected List<URI> existingLinkedSnapshotIds;

    @Param(value = LINKED_SNAPSHOT_NAME, required = false)
    protected String linkedSnapshotName;

    @Param(value = LINKED_SNAPSHOT_COUNT, required = false)
    protected Integer linkedSnapshotCount;

    @Param(value = LINKED_SNAPSHOT_COPYMODE, required = false)
    protected String linkedSnapshotCopyMode;

    @Override
    public void precheck() throws Exception {
        if (linkedSnapshotName != null && !linkedSnapshotName.isEmpty()) {
            // Can not relink an existing snapshot and link a new snapshot at the same time
            if (existingLinkedSnapshotIds != null && !existingLinkedSnapshotIds.isEmpty()) {
                ExecutionUtils.fail("failTask.LinkSnapshotSessionForApplication.linkNewAndExistingSnapshot.precheck", new Object[] {},
                        new Object[] {});
            }
            // If trying to create a new Snapshot Session and the optional linkedSnapshotName
            // is populated, make sure that linkedSnapshotCount > 0.
            if (linkedSnapshotCount == null || linkedSnapshotCount.intValue() <= 0) {
                ExecutionUtils.fail("failTask.LinkSnapshotSessionForApplication.linkedSnapshotCount.precheck", new Object[] {},
                        new Object[] {});
            }
            // Ensure that copy mode is selected
            if (linkedSnapshotCopyMode == null
                    || !(BlockProvider.LINKED_SNAPSHOT_COPYMODE_VALUE.equals(linkedSnapshotCopyMode)
                    || BlockProvider.LINKED_SNAPSHOT_NOCOPYMODE_VALUE.equals(linkedSnapshotCopyMode))) {
                ExecutionUtils.fail("failTask.LinkSnapshotSessionForApplication.linkedSnapshotCopyMode.precheck", new Object[] {},
                        new Object[] {});
            }
        } else if (existingLinkedSnapshotIds == null || existingLinkedSnapshotIds.isEmpty()) {
            // If we get here, the user hasn't selected existing linked snapshots to relink and also hasn't
            // filled in the correct information needed for linking a new snapshot.
            ExecutionUtils.fail("failTask.LinkSnapshotSessionForApplication.linkAtLeastOneSnapshot.precheck", new Object[] {},
                    new Object[] {});
        }
    }

    @Override
    public void execute() throws Exception {
        NamedVolumesList volList = getClient().application().getVolumeByApplication(applicationId);
        List<URI> snapshotSessionIds = BlockStorageUtils.getSingleSnapshotSessionPerSubGroup(applicationId, copySet,
                volList, subGroups);
        Tasks<? extends DataObjectRestRep> tasks = execute(new LinkSnapshotSessionForApplication(applicationId, snapshotSessionIds,
                existingLinkedSnapshotIds, linkedSnapshotCopyMode, linkedSnapshotCount, linkedSnapshotName));
        addAffectedResources(tasks);
    }
}
