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
    private final URI bucketId;
    private final String softQuota;
    private final String hardQuota;
    private final String retention;

    public UpdateBucket(String bucketId, String softQuota, String hardQuota, String retention) {
        this(uri(bucketId), softQuota, hardQuota, retention);
    }

    public UpdateBucket(URI bucketId, String softQuota, String hardQuota, String retention) {

        this.bucketId = bucketId;
        this.softQuota = softQuota;
        this.hardQuota = hardQuota;
        this.retention = retention;

        provideDetailArgs(bucketId);
    }

    @Override
    public Task<BucketRestRep> doExecute() throws Exception {
        BucketUpdateParam create = new BucketUpdateParam();
        if (softQuota != null) {
            create.setSoftQuota(softQuota);
        }
        if (hardQuota != null) {
            create.setHardQuota(hardQuota);
        }
        if (retention != null) {
            create.setRetention(retention);
        }

        return getClient().objectBuckets().update(bucketId, create);
    }
}
