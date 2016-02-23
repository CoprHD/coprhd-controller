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
import com.emc.sa.service.vipr.application.tasks.RestoreApplicationFullCopy;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.client.Tasks;

@Service("RestoreApplicationFullCopy")
public class RestoreApplicationFullCopyService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.COPY_NAME)
    private String copyName;

    @Param(value = ServiceParams.APPLICATION_SUB_GROUP, required = false)
    private List<String> applicationSubGroup;

    @Override
    public void execute() throws Exception {
        Tasks<? extends DataObjectRestRep> tasks = execute(new RestoreApplicationFullCopy(applicationId, applicationSubGroup, copyName));
        addAffectedResources(tasks);
    }
}
