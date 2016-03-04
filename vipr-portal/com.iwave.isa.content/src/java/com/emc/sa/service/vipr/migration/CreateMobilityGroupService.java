/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.migration;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.migration.tasks.CreateMobilityGroup;
import com.emc.storageos.model.application.VolumeGroupRestRep;

@Service("CreateMobilityGroup")
public class CreateMobilityGroupService extends ViPRService {

    @Param(ServiceParams.NAME)
    private String name;

    @Param(value = ServiceParams.DESCRIPTION, required = false)
    private String description;

    @Param(ServiceParams.MIGRATION_TYPE)
    private String migrationType;

    @Param(ServiceParams.GROUP_BY)
    private String groupBy;

    @Override
    public void execute() throws Exception {
        VolumeGroupRestRep application = execute(new CreateMobilityGroup(name, description, migrationType, groupBy));
        addAffectedResource(application);
    }
}