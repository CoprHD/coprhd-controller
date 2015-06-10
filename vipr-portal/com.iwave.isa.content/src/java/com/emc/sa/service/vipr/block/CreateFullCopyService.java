/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.COUNT;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.Task;

@Service("CreateFullCopy")
public class CreateFullCopyService extends ViPRService {
    @Param(VOLUMES)
    protected URI volumeId;

    @Param(NAME)
    protected String name;

    @Param(COUNT)
    protected Integer count;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        BlockStorageUtils.getVolume(volumeId);
    }

    @Override
    public void execute() throws Exception {
        Tasks<VolumeRestRep> copies = BlockStorageUtils.createFullCopy(volumeId, name, count);
        for (Task<VolumeRestRep> copy : copies.getTasks()) {
            logInfo("create.full.copy.service", copy.getResource().getName(), copy.getResource().getId());
        }
    }
}
