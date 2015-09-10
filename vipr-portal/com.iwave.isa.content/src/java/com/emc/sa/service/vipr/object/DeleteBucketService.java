/*
 * Copyright (c) 2012-2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.object;

import static com.emc.sa.service.ServiceParams.BUCKET;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("DeleteBucket")
public class DeleteBucketService extends ViPRService  {
    @Param(BUCKET)
    protected String bucketId;
    
    @Override
    public void precheck() {
        ObjectStorageUtils.getBucketResource(uri(bucketId));
    }

    @Override
    public void execute() {
        ObjectStorageUtils.removeBucketResource(uri(bucketId));
    }
}
