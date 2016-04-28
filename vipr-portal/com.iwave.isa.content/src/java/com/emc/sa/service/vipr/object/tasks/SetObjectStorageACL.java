/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.object.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.object.ObjectStorageUtils;
import com.emc.sa.service.vipr.object.ObjectStorageUtils.ObjectStorageACL;
import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.object.BucketACL;
import com.emc.storageos.model.object.ObjectBucketACLUpdateParams;
import com.emc.storageos.model.object.BucketRestRep;
import com.emc.vipr.client.Task;

public class SetObjectStorageACL extends WaitForTask<BucketRestRep> {
    private final URI bucketId;
    private final ObjectStorageACL acl;

    public SetObjectStorageACL(URI bucketId, ObjectStorageACL acl) {
        this.bucketId = bucketId;
        this.acl = acl;
        provideDetailArgs(bucketId);
    }

    @Override
    protected Task<BucketRestRep> doExecute() throws Exception {
        ObjectBucketACLUpdateParams aclUpdate = new ObjectBucketACLUpdateParams();
        BucketACL aclsToAdd = ObjectStorageUtils.createBucketACLs(acl);
        aclUpdate.setAclToAdd(aclsToAdd);
        return getClient().objectBuckets().updateBucketACL(bucketId, aclUpdate);
    }
    
}
