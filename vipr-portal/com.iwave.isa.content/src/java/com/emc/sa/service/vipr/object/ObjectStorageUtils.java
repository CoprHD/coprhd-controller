/*
 * Copyright (c) 2012-2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.object;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResource;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.addRollback;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.execute;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.net.URI;

import com.emc.sa.service.vipr.object.tasks.CreateBucket;
import com.emc.sa.service.vipr.object.tasks.DeactivateBucket;

import com.emc.storageos.model.object.BucketRestRep;
import com.emc.vipr.client.Task;



public class ObjectStorageUtils {
    //(bucketName, virtualPool, project, softQuota, hardQuota, retention, namespace, tenant, owner)
    public static URI createBucket(String bucketName, URI virtualPoolId, URI projectId, Double softQuota, Double hardQuota,
            Double retention, URI namespace, URI tenant, URI owner) {
        Task<BucketRestRep> task = execute(new CreateBucket(bucketName, virtualPoolId, projectId, softQuota, hardQuota, retention,
                namespace, tenant, owner));
        addAffectedResource(task);
        URI bucketId = task.getResourceId();
        addRollback(new DeactivateBucket(bucketId));
        logInfo("file.storage.filesystem.task", bucketId, task.getOpId());
        return bucketId;
    }
}
