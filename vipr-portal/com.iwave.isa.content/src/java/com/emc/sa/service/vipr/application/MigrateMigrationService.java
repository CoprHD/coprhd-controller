/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.sa.service.vipr.application;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.application.tasks.MigrateApplication;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.client.Tasks;

/**
 * @author cgarber
 *
 */
@Service("MigrateMigration")
public class MigrateMigrationService extends ViPRService {

    @Param(ServiceParams.MOBILITY_GROUP)
    private URI mobilityGroupId;

    /* (non-Javadoc)
     * @see com.emc.sa.engine.service.ExecutionService#execute()
     */
    @Override
    public void execute() throws Exception {
        Tasks<? extends DataObjectRestRep> tasks = execute(new MigrateApplication(mobilityGroupId));
        addAffectedResources(tasks);
    }

}
