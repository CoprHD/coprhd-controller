/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.application.tasks.ResynchronizeSnapshotForApplication;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.vipr.client.Tasks;

@Service("ResynchronizeSnapshotOfApplication")
public class ResynchronizeSnapshotOfApplicationService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.APPLICATION_COPY_SETS)
    private String applicationCopySet;

    @Param(ServiceParams.APPLICATION_SUB_GROUP)
    protected List<String> subGroups;

    @Override
    public void execute() throws Exception {

        // get list of volumes in application
        NamedVolumesList applicationVolumes = getClient().application().getVolumeByApplication(applicationId);

        List<URI> snapshotIds = BlockStorageUtils.getSingleSnapshotPerSubGroupAndStorageSystem(applicationId, applicationCopySet, applicationVolumes,
                subGroups);
        Tasks<? extends DataObjectRestRep> tasks = execute(new ResynchronizeSnapshotForApplication(applicationId, snapshotIds));

        addAffectedResources(tasks);

    }
}
