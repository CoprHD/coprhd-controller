package com.emc.sa.service.vipr.object;

import static com.emc.sa.service.ServiceParams.RETENTION;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.TENANT;
import static com.emc.sa.service.ServiceParams.OWNER;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.NAMESPACE;
import static com.emc.sa.service.ServiceParams.SOFT_QUOTA;
import static com.emc.sa.service.ServiceParams.HARD_QUOTA;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("CreateBucket")
public class CreateBucketService extends ViPRService  {
    
    @Param(NAME)
    protected String bucketName;
    
    @Param(VIRTUAL_POOL)
    protected URI virtualPool;

    @Param(PROJECT)
    protected URI project;

    @Param(value = SOFT_QUOTA, required = false)
    protected Double softQuota;
    
    @Param(value = HARD_QUOTA, required = false)
    protected Double hardQuota;
    
    @Param(value = RETENTION, required = false)
    protected Double retention;

    @Param(value = NAMESPACE, required = false)
    protected URI namespace;
    
    @Param(value = TENANT, required = false)
    protected URI tenant;
    
    @Param(value = OWNER, required = false)
    protected URI owner;

    @Override
    public void execute() throws Exception {
        ObjectStorageUtils.createBucket(bucketName, virtualPool, project, softQuota, hardQuota, retention, namespace, tenant, owner);
    }
}
