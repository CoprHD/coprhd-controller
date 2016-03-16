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
import com.emc.sa.service.vipr.application.tasks.DetachApplicationFullCopy;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.client.Tasks;

@Service("DetachApplicationFullCopy")
public class DetachApplicationFullCopyService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.APPLICATION_COPY_SETS)
    protected String fullCopyName;

    @Param(ServiceParams.APPLICATION_SUB_GROUP)
    protected List<String> subGroups;

    @Override
    public void execute() throws Exception {

        List<URI> fullCopyIds = BlockStorageUtils.getSingleFullCopyPerSubGroupAndStorageSystem(applicationId, fullCopyName,
                subGroups);

        Tasks<? extends DataObjectRestRep> tasks = execute(new DetachApplicationFullCopy(applicationId, fullCopyIds));
        addAffectedResources(tasks);
    }
}
