/*
 * Copyright (c) 2012-2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.object;

import static com.emc.sa.service.ServiceParams.HARD_QUOTA;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.OWNER;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.RETENTION;
import static com.emc.sa.service.ServiceParams.SOFT_QUOTA;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("CreateBucket")
public class CreateBucketService extends ViPRService {

    @Param(NAME)
    protected String bucketName;

    @Param(VIRTUAL_POOL)
    protected URI virtualPool;

    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;

    @Param(PROJECT)
    protected URI project;

    @Param(value = SOFT_QUOTA)
    protected Double softQuota;

    @Param(value = HARD_QUOTA)
    protected Double hardQuota;

    @Param(value = RETENTION)
    protected String retention;

    @Param(value = OWNER)
    protected String owner;

    @Override
    public void execute() throws Exception {
        ObjectStorageUtils.createBucket(bucketName, virtualArray, virtualPool, project, softQuota, hardQuota, retention, owner);
    }
}
