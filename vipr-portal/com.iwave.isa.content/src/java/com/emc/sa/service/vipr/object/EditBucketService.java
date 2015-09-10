package com.emc.sa.service.vipr.object;

import static com.emc.sa.service.ServiceParams.BUCKETS;
import static com.emc.sa.service.ServiceParams.HARD_QUOTA;
import static com.emc.sa.service.ServiceParams.RETENTION;
import static com.emc.sa.service.ServiceParams.SOFT_QUOTA;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("EditBucket")
public class EditBucketService extends ViPRService {
    
    @Param(BUCKETS)
    protected String bucketId;
    
    @Param(value = SOFT_QUOTA, required = false)
    protected Double softQuota;
    
    @Param(value = HARD_QUOTA, required = false)
    protected Double hardQuota;
    
    @Param(value = RETENTION, required = false)
    protected Double retention;
    
    @Override
    public void execute() throws Exception {
        ObjectStorageUtils.editBucketResource(uri(bucketId), softQuota, hardQuota, retention);
    }
}
