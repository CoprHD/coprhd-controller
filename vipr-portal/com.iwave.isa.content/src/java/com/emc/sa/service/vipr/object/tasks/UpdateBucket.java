/*
 * Copyright (c) 2012-2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.object.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.object.BucketRestRep;
import com.emc.storageos.model.object.BucketUpdateParam;
import com.emc.vipr.client.Task;

public class UpdateBucket extends WaitForTask<BucketRestRep> {
    private URI bucketId;
    private Double softQuota;
    private Double hardQuota;
    private Double retention;
    
    public UpdateBucket(String bucketId, Double softQuota, Double hardQuota, Double retention) {
        this(uri(bucketId), softQuota, hardQuota, retention);
    }

    public UpdateBucket(URI bucketId, Double softQuota, Double hardQuota, Double retention) {

        this.bucketId = bucketId;
        this.softQuota = softQuota;
        this.hardQuota = hardQuota;
        this.retention = retention;

        provideDetailArgs(bucketId);
    }

    @Override
    public Task<BucketRestRep> doExecute() throws Exception {
        BucketUpdateParam create = new BucketUpdateParam();
        create.setSoftQuota(softQuota.toString());
        create.setHardQuota(hardQuota.toString());
        create.setRetention(retention.toString());

        return getClient().objectBuckets().update(bucketId, create);
    }
}
