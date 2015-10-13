/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.COUNT;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.STORAGE_TYPE;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;

@Service("CreateFullCopy")
public class CreateFullCopyService extends ViPRService {

    @Param(value = STORAGE_TYPE, required = false)
    protected String storageType;

    @Param(VOLUMES)
    protected URI volumeId;

    @Param(NAME)
    protected String name;

    @Param(COUNT)
    protected Integer count;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            BlockStorageUtils.getVolume(volumeId);
        }
    }

    @Override
    public void execute() throws Exception {
        Tasks<? extends DataObjectRestRep> tasks;
        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            tasks = BlockStorageUtils.createFullCopy(volumeId, name, count);
            addAffectedResources(tasks);
        } else {
            tasks = ConsistencyUtils.createFullCopy(volumeId, name, count);
            addAffectedResources(tasks);
        }
        for (Task<? extends DataObjectRestRep> copy : tasks.getTasks()) {
            logInfo("create.full.copy.service", copy.getResource().getName(), copy.getResource().getId());
        }
    }
}
