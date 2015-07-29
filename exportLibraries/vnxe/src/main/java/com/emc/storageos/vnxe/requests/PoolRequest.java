/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import com.emc.storageos.vnxe.models.VNXePool;

public class PoolRequest extends KHRequests<VNXePool> {
    private static final String URL = "/api/instances/pool/";

    public PoolRequest(KHClient client, String id) {
        super(client);
        _url = URL + id;
    }

    public VNXePool get() {
        return getDataForOneObject(VNXePool.class);

    }

}
