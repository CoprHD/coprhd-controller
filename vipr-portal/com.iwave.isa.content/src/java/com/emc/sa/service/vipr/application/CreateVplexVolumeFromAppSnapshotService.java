/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.application.tasks.CreateVplexVolumeFromAppSnapshot;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.client.Tasks;

@Service("CreateVplexVolumeFromAppSnapshot")
public class CreateVplexVolumeFromAppSnapshotService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.APPLICATION_COPY_SETS)
    private String applicationCopySet;

    @Param(ServiceParams.APPLICATION_SUB_GROUP)
    private List<String> subGroups;

    @Override
    public void execute() throws Exception {

        List<URI> snapshotIds = BlockStorageUtils.getSingleSnapshotPerSubGroupAndStorageSystem(applicationId, applicationCopySet,
                subGroups);
        Tasks<? extends DataObjectRestRep> tasks = execute(new CreateVplexVolumeFromAppSnapshot(applicationId, applicationCopySet, subGroups));

        addAffectedResources(tasks);

    }
}
