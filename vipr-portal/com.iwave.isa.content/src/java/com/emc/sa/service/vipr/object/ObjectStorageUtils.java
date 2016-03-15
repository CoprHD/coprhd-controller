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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.vipr.object.tasks.CreateBucket;
import com.emc.sa.service.vipr.object.tasks.DeactivateBucket;
import com.emc.sa.service.vipr.object.tasks.GetBucketResource;
import com.emc.sa.service.vipr.object.tasks.SetObjectStorageACL;
import com.emc.sa.service.vipr.object.tasks.UpdateBucket;
import com.emc.sa.util.DiskSizeConversionUtils;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.object.BucketACE;
import com.emc.storageos.model.object.BucketACL;
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
    
    public static BucketACL createBucketACLs(ObjectStorageACL acl) {
        BucketACL aclsToAdd = new BucketACL();
        List<BucketACE> aclList = new ArrayList<BucketACE>();

        BucketACE bucketAce = new BucketACE();
        
        if (acl.aclType.equalsIgnoreCase("GROUP")) {
            bucketAce.setGroup(acl.aclName);
        } else if (acl.aclType.equalsIgnoreCase("USER")) {
            bucketAce.setUser(acl.aclName);
        } else {
            bucketAce.setCustomGroup(acl.aclName);
        }
        
        if (!StringUtils.isEmpty(acl.aclDomain)) {
            bucketAce.setDomain(acl.aclDomain);
        }
        
        bucketAce.setPermissions(StringUtils.join(acl.aclPermission, "|").toLowerCase());
        
        aclList.add(bucketAce);
        aclsToAdd.setBucketACL(aclList);
        return aclsToAdd;
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
    
    public static void setObjectShareACL(URI bucketId, ObjectStorageACL acl) {
        Task<BucketRestRep> task = execute(new SetObjectStorageACL(bucketId, acl));
        addAffectedResource(task);
        logInfo("object.bucket.acl", bucketId, task.getOpId());
    }
    
    public static List<String> getInvalidObjectACL(ObjectStorageACL objectACL) {
        List<String> names = new ArrayList<String>();
        if (StringUtils.contains(objectACL.aclName, "\\")) {
            names.add(objectACL.aclName);
        }
        
        return names;
    }

    public static ObjectStorageACL[] clearEmptyObjectACLs(ObjectStorageACL[] objectACLs) {
        List<ObjectStorageUtils.ObjectStorageACL> toRemove = new ArrayList<ObjectStorageUtils.ObjectStorageACL>();
        for (ObjectStorageUtils.ObjectStorageACL acl : objectACLs) {
            if (acl.aclName != null && acl.aclName.isEmpty()) {
                toRemove.add(acl);
            }
        }

        for (ObjectStorageUtils.ObjectStorageACL element : toRemove) {
            objectACLs = (ObjectStorageUtils.ObjectStorageACL[]) ArrayUtils.removeElement(objectACLs, element);
        }

        return objectACLs;
    }
    
    public static class ObjectStorageACL {
        @Param
        public String aclType;

        @Param
        public String aclName;
        
        @Param
        public String aclDomain;

        @Param
        public List<String> aclPermission;
    }
}
