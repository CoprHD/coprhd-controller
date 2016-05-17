/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import com.emc.storageos.vnxe.models.VNXePool;

public class PoolRequest extends KHRequests<VNXePool> {
    private static final String URL = "/api/instances/pool/";
    private static final String FIELDS = "raidType,tiers,sizeTotal,sizeFree,sizeSubscribed,name,isEmpty,poolFastVP,isFASTCacheEnabled,health";

    public PoolRequest(KHClient client, String id) {
        super(client);
        _url = URL + id;
        _fields = FIELDS;
    }

    public VNXePool get() {
        return getDataForOneObject(VNXePool.class);

    }

}
