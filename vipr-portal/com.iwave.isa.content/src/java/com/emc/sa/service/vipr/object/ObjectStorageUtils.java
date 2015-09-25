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
import java.util.List;

import com.emc.sa.service.vipr.object.tasks.CreateBucket;
import com.emc.sa.service.vipr.object.tasks.DeactivateBucket;
import com.emc.sa.service.vipr.object.tasks.GetBucketResource;
import com.emc.sa.service.vipr.object.tasks.UpdateBucket;
import com.emc.sa.util.DiskSizeConversionUtils;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.object.BucketRestRep;
import com.emc.vipr.client.Task;
import com.google.common.collect.Lists;

public class ObjectStorageUtils {

    public static URI createBucket(String bucketName, URI virtualArray, URI virtualPoolId, URI projectId, Double softQuota,
            Double hardQuota, String retention, String owner) {
        String softQuotaSize = gbToQuotaSize(softQuota);
        String hardQuotaSize = gbToQuotaSize(hardQuota);
        Task<BucketRestRep> task = execute(new CreateBucket(bucketName, virtualArray, virtualPoolId, projectId, softQuotaSize, 
                hardQuotaSize, retention, owner));
        addAffectedResource(task);
        URI bucketId = task.getResourceId();
        addRollback(new DeactivateBucket(bucketId));
        logInfo("object.bucket.task", bucketId, task.getOpId());
        return bucketId;
    }

    public static DataObjectRestRep getBucketResource(URI resourceId) {
        return execute(new GetBucketResource(resourceId));
    }

    public static List<DataObjectRestRep> getBucketResources(List<URI> resourceIds) {
        List<DataObjectRestRep> bucketResources = Lists.newArrayList();
        for (URI resourceId : resourceIds) {
            bucketResources.add(getBucketResource(resourceId));
        }
        return bucketResources;
    }

    public static void removeBucketResource(URI bucketResourceId) {
        Task<BucketRestRep> task = execute(new DeactivateBucket(bucketResourceId));
        addAffectedResource(task);
    }

    public static void editBucketResource(URI bucketResourceId, Double softQuota, Double hardQuota, String retention) {
        String softQuotaSize = gbToQuotaSize(softQuota);
        String hardQuotaSize = gbToQuotaSize(hardQuota);
        Task<BucketRestRep> task = execute(new UpdateBucket(bucketResourceId, softQuotaSize, hardQuotaSize, retention));
        addAffectedResource(task);
    }
    
    public static String gbToQuotaSize(double sizeInGB) {
        return String.valueOf(DiskSizeConversionUtils.gbToBytes(sizeInGB));
    }
}
