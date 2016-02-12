/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.application.tasks.CreateCloneOfApplication;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.vipr.client.Tasks;

@Service("CreateCloneOfApplication")
public class CreateCloneOfApplicationService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.NAME)
    protected String name;

    @Param(ServiceParams.COUNT)
    protected Integer count;

    @Param(ServiceParams.VIRTUAL_ARRAY)
    protected URI virtualArrayId;

    @Override
    public void execute() throws Exception {
        Tasks<? extends DataObjectRestRep> tasks = execute(new CreateCloneOfApplication(applicationId, name, virtualArrayId, count));
        addAffectedResources(tasks);
    }
    
    @Override
    public void precheck() throws Exception {
        NamedVolumesList volList = getClient().application().getVolumeByApplication(applicationId);
        if (volList == null || volList.getVolumes() == null || volList.getVolumes().isEmpty()) {
            ExecutionUtils.fail("failTask.CreateCloneOfApplicationService.volumeId.precheck", new Object[] {});
        }
    }
}
