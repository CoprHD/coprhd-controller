package com.emc.sa.service.vipr.object.tasks;

import static com.emc.sa.util.ArrayUtil.safeArrayCopy;

import java.net.URI;

import com.emc.sa.service.vipr.object.ObjectStorageUtils;
import com.emc.sa.service.vipr.object.ObjectStorageUtils.ObjectStorageACLs;
import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.object.BucketACL;
import com.emc.storageos.model.object.ObjectBucketACLUpdateParams;
import com.emc.storageos.model.object.BucketRestRep;
import com.emc.vipr.client.Task;

public class SetObjectStorageACL extends WaitForTask<BucketRestRep> {
    private final String shareName;
    private final URI bucketId;
    private final ObjectStorageACLs[] acls;

    public SetObjectStorageACL(URI bucketId, String shareName, ObjectStorageACLs[] acls) {
        this.shareName = shareName;
        this.bucketId = bucketId;
        this.acls = safeArrayCopy(acls);
        provideDetailArgs(bucketId, shareName);
    }

    @Override
    protected Task<BucketRestRep> doExecute() throws Exception {
        ObjectBucketACLUpdateParams aclUpdate = new ObjectBucketACLUpdateParams();
        BucketACL aclsToAdd = ObjectStorageUtils.createBucketACLs(acls);
        aclUpdate.setAclToAdd(aclsToAdd);
        return getClient().objectBuckets().updateBucketACL(bucketId, aclUpdate);
    }
    
}
