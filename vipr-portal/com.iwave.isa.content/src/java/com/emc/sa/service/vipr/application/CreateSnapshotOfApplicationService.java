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
import com.emc.sa.service.vipr.application.tasks.CreateSnapshotForApplication;
import com.emc.sa.service.vipr.application.tasks.CreateSnapshotSessionForApplication;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.vipr.client.Tasks;

@Service("CreateSnapshotOfApplication")
public class CreateSnapshotOfApplicationService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.NAME)
    protected String name;

    @Param(ServiceParams.APPLICATION_SITE)
    protected URI virtualArrayId;

    @Param(ServiceParams.HIGH_AVAILABILITY)
    protected Boolean highAvailability;

    @Param(ServiceParams.COUNT)
    protected Integer count;

    @Param(ServiceParams.READ_ONLY)
    protected Boolean readOnly;

    @Param(ServiceParams.APPLICATION_SUB_GROUP)
    protected List<String> subGroups;

    @Override
    public void execute() throws Exception {

        NamedVolumesList applicationVolumes = getClient().application().getVolumeByApplication(applicationId);
        List<URI> volumeIds = BlockStorageUtils.getSingleVolumePerSubGroup(applicationVolumes, subGroups);

        Tasks<? extends DataObjectRestRep> tasks = null;

        if (BlockStorageUtils.containsVmax3Volume(applicationVolumes)) {
            tasks = execute(new CreateSnapshotSessionForApplication(applicationId, volumeIds, name,
                    highAvailability));
        } else {
            tasks = execute(new CreateSnapshotForApplication(applicationId, volumeIds, name, readOnly,
                    highAvailability));
        }
        addAffectedResources(tasks);
    }
}
