/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.migration;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.migration.tasks.DeleteMobilityGroup;

@Service("DeleteMobilityGroup")
public class DeleteMobilityGroupService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Override
    public void execute() throws Exception {
        execute(new DeleteMobilityGroup(applicationId));
    }
}