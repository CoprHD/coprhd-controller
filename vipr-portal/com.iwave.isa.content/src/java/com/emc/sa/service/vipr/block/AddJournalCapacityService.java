/*
 * Copyright 2012-2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.CONSISTENCY_GROUP;
import static com.emc.sa.service.ServiceParams.NUMBER_OF_VOLUMES;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("AddJournalCapacity")
public class AddJournalCapacityService extends ViPRService {

    @Param(VIRTUAL_POOL)
    protected URI virtualPool;

    @Param(PROJECT)
    protected URI project;

    @Param(SIZE_IN_GB)
    protected Double sizeInGb;

    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;

    @Param(value = NUMBER_OF_VOLUMES, required = false)
    protected Integer count;

    @Param(value = CONSISTENCY_GROUP, required = false)
    protected URI consistencyGroup;

    @Override
    public void execute() throws Exception {
        BlockStorageUtils.addJournalCapacity(project, virtualArray, virtualPool, sizeInGb, count,
                consistencyGroup);
    }
}
