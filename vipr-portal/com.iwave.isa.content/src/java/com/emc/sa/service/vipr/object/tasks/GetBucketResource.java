/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.object.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.object.BucketRestRep;
import com.emc.vipr.client.ViPRCoreClient;

public class GetBucketResource extends ViPRExecutionTask<DataObjectRestRep> {

    private URI resourceId;

    public GetBucketResource(String resourceId) {
        this(uri(resourceId));
    }

    public GetBucketResource(URI resourceId) {
        this.resourceId = resourceId;
        provideDetailArgs(resourceId);
    }

    @Override
    public DataObjectRestRep executeTask() throws Exception {
        ViPRCoreClient client = getClient();
        
        BucketRestRep bucket = client.objectBuckets().get(resourceId);
        if (bucket != null) {
            return bucket;
        }

        throw stateException("GetBucketResource.illegalState.notFound", resourceId);
    }
}