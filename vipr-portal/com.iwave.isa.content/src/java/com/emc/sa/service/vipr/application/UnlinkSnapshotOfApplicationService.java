/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.application.tasks.UnlinkSnapshotSessionForApplication;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.vipr.client.Tasks;

@Service("UnlinkSnapshotOfApplication")
public class UnlinkSnapshotOfApplicationService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.APPLICATION_COPY_SETS)
    protected String applicationCopySet;

    @Param(ServiceParams.APPLICATION_SUB_GROUP)
    protected List<String> subGroups;

    @Param(value = LINKED_SNAPSHOT, required = false)
    protected List<String> existingLinkedSnapshotIds;

    @Override
    public void execute() throws Exception {
        NamedVolumesList volList = getClient().application().getVolumeByApplication(applicationId);
        List<URI> snapshotSessionIds = BlockStorageUtils.getSingleSnapshotSessionPerSubGroup(applicationId, applicationCopySet,
                volList, subGroups);
        Tasks<? extends DataObjectRestRep> tasks = execute(new UnlinkSnapshotSessionForApplication(applicationId, snapshotSessionIds,
                existingLinkedSnapshotIds));
        addAffectedResources(tasks);
    }
}
