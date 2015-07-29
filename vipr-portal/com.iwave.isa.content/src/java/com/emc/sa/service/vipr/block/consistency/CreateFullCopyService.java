/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.consistency;

import static com.emc.sa.service.ServiceParams.CONSISTENCY_GROUP;
import static com.emc.sa.service.ServiceParams.COUNT;
import static com.emc.sa.service.ServiceParams.NAME;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;

@Service("ConsistencyGroupCreateFullCopy")
public class CreateFullCopyService extends ViPRService {
    @Param(CONSISTENCY_GROUP)
    protected URI consistencyGroupId;

    @Param(NAME)
    protected String name;

    @Param(COUNT)
    protected Integer count;

    @Override
    public void execute() throws Exception {
        Tasks<BlockConsistencyGroupRestRep> copies = ConsistencyUtils.createFullCopy(consistencyGroupId, name, count);
        for (Task<BlockConsistencyGroupRestRep> copy : copies.getTasks()) {
            logInfo("create.full.copy.service", copy.getResource().getName(), copy.getResource().getId());
        }
    }
}
