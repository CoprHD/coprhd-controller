package com.emc.sa.service.vipr.object;

import static com.emc.sa.service.ServiceParams.BUCKETS;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("DeleteBucket")
public class DeleteBucketService extends ViPRService  {
    @Param(BUCKETS)
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
