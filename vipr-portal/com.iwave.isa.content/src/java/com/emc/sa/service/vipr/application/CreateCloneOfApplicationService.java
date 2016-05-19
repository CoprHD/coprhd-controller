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
import com.emc.sa.service.vipr.application.tasks.CreateCloneOfApplication;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.vipr.client.Tasks;

@Service("CreateCloneOfApplication")
public class CreateCloneOfApplicationService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.APPLICATION_COPY_SETS)
    private String name;

    @Param(value = ServiceParams.APPLICATION_SITE, required = false)
    private String virtualArrayId;

    @Param(ServiceParams.APPLICATION_SUB_GROUP)
    protected List<String> subGroups;

    @Override
    public void execute() throws Exception {
        NamedVolumesList volumesToUse = BlockStorageUtils.getVolumesBySite(getClient(), virtualArrayId, applicationId);

        List<URI> volumeIds = BlockStorageUtils.getSingleVolumePerSubGroupAndStorageSystem(volumesToUse, subGroups);

        Tasks<? extends DataObjectRestRep> tasks = execute(
                new CreateCloneOfApplication(applicationId, name, volumeIds));
        addAffectedResources(tasks);
    }
}
