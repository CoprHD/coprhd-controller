package com.emc.sa.service.vipr.object.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.object.BucketDeleteParam;
import com.emc.storageos.model.object.BucketRestRep;
import com.emc.vipr.client.Task;

public class DeactivateBucket extends WaitForTask<BucketRestRep> {
    private final URI bucketId;

    public DeactivateBucket(String bucketId) {
        this(uri(bucketId));
    }

    public DeactivateBucket(URI bucketId) {
        this.bucketId = bucketId;
        provideDetailArgs(bucketId);
    }

    public URI getBucketId() {
        return bucketId;
    }

    @Override
    protected Task<BucketRestRep> doExecute() throws Exception {
        BucketDeleteParam param = new BucketDeleteParam();
        param.setForceDelete(true);
        return getClient().objectBuckets().deactivate(bucketId, param);
    }
}
