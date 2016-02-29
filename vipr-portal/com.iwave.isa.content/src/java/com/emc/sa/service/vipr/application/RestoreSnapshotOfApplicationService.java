/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import java.net.URI;
import java.util.List;

import com.emc.sa.asset.providers.BlockProvider;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.application.tasks.RestoreSnapshotForApplication;
import com.emc.sa.service.vipr.application.tasks.RestoreSnapshotSessionForApplication;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.vipr.client.Tasks;

@Service("RestoreSnapshotOfApplication")
public class RestoreSnapshotOfApplicationService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.APPLICATION_SNAPSHOT_TYPE)
    private String snapshotType;

    @Param(ServiceParams.APPLICATION_COPY_SETS)
    private String applicationCopySet;

    @Param(ServiceParams.APPLICATION_SUB_GROUP)
    protected List<String> subGroups;

    @Override
    public void execute() throws Exception {

        // get list of volumes in application
        NamedVolumesList applicationVolumes = getClient().application().getVolumeByApplication(applicationId);
        Tasks<? extends DataObjectRestRep> tasks = null;

        if (snapshotType != null && snapshotType.equalsIgnoreCase(BlockProvider.SNAPSHOT_SESSION_TYPE_VALUE)) {
            List<URI> snapshotSessionIds = BlockStorageUtils.getSingleSnapshotSessionPerSubGroupAndStorageSystem(applicationId,
                    applicationCopySet,
                    applicationVolumes, subGroups);
            tasks = execute(new RestoreSnapshotSessionForApplication(applicationId, snapshotSessionIds));
        } else {
            List<URI> snapshotIds = BlockStorageUtils.getSingleSnapshotPerSubGroupAndStorageSystem(applicationId, applicationCopySet,
                    applicationVolumes,
                    subGroups);
            tasks = execute(new RestoreSnapshotForApplication(applicationId, snapshotIds));
        }
        addAffectedResources(tasks);

    }
}
