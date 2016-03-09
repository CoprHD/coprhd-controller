/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.application.tasks.CreateApplication;
import com.emc.storageos.model.application.VolumeGroupRestRep;

@Service("CreateApplication")
public class CreateApplicationService extends ViPRService {

    @Param(ServiceParams.NAME)
    private String name;

    @Param(value = ServiceParams.DESCRIPTION, required = false)
    private String description;

    @Override
    public void execute() throws Exception {
        VolumeGroupRestRep application = execute(new CreateApplication(name, description));
        addAffectedResource(application);
    }
}
