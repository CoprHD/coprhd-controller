/*
 * Copyright (c) 2012-2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.object;

import static com.emc.sa.service.ServiceParams.BUCKET;
import static com.emc.sa.service.ServiceParams.HARD_QUOTA;
import static com.emc.sa.service.ServiceParams.RETENTION;
import static com.emc.sa.service.ServiceParams.SOFT_QUOTA;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("EditBucket")
public class EditBucketService extends ViPRService {

    @Param(BUCKET)
    protected String bucketId;

    @Param(value = SOFT_QUOTA)
    protected Double softQuota;

    @Param(value = HARD_QUOTA)
    protected Double hardQuota;

    @Param(value = RETENTION)
    protected String retention;

    @Override
    public void execute() throws Exception {
        ObjectStorageUtils.editBucketResource(uri(bucketId), softQuota, hardQuota, retention);
    }
}
