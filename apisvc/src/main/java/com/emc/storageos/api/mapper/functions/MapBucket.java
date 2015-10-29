/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.BucketMapper;
import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.model.object.BucketRestRep;
import com.google.common.base.Function;

public class MapBucket implements Function<Bucket, BucketRestRep> {
    public static final MapBucket instance = new MapBucket();

    public static MapBucket getInstance() {
        return instance;
    }

    private MapBucket() {
    }

    @Override
    public BucketRestRep apply(Bucket share) {
        return BucketMapper.map(share);
    }
}
