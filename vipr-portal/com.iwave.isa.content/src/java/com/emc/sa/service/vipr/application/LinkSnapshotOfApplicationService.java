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

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.application.tasks.GetBlockSnapshotSessionList;
import com.emc.sa.service.vipr.application.tasks.LinkSnapshotSessionForApplication;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.BlockSnapshotSessionList;
import com.emc.vipr.client.Tasks;

@Service("LinkSnapshotOfApplication")
public class LinkSnapshotOfApplicationService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.APPLICATION_COPY_SETS)
    protected String copySet;

    @Param(ServiceParams.APPLICATION_SUB_GROUP)
    protected List<URI> subGroups;

    @Param(value = LINKED_SNAPSHOT, required = false)
    protected List<URI> existingLinkedSnapshotIds;

    @Param(value = LINKED_SNAPSHOT_NAME, required = false)
    protected String linkedSnapshotName;

    @Param(value = LINKED_SNAPSHOT_COUNT, required = false)
    protected Integer linkedSnapshotCount;

    @Param(value = LINKED_SNAPSHOT_COPYMODE, required = false)
    protected String linkedSnapshotCopyMode;

    @Override
    public void execute() throws Exception {
        BlockSnapshotSessionList snapshotSessions = execute(new GetBlockSnapshotSessionList(applicationId, copySet));
        Tasks<? extends DataObjectRestRep> tasks = execute(new LinkSnapshotSessionForApplication(applicationId, snapshotSessions
                .getSnapSessionRelatedResourceList()
                .get(0).getId(),
                existingLinkedSnapshotIds, linkedSnapshotCopyMode, linkedSnapshotCount, linkedSnapshotName));
        addAffectedResources(tasks);
    }
}
