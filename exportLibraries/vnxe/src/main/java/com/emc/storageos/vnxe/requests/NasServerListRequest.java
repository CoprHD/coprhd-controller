/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import com.emc.storageos.vnxe.models.VNXeNasServer;

public class NasServerListRequest extends KHRequests<VNXeNasServer> {
    private static final String URL = "/api/types/nasServer/instances";
    private static final String URL_INSTANCE = "/api/instances/nasServer/";
    private static final String FIELDS = "name,isReplicationDestination";

    public NasServerListRequest(KHClient client) {
        super(client);
        _url = URL;
        _fields = FIELDS;
    }

    public List<VNXeNasServer> get() {
        return getDataForObjects(VNXeNasServer.class);

    }

    public VNXeNasServer get(String id) {
        _url = URL_INSTANCE + id;
        return getDataForOneObject(VNXeNasServer.class);

    }

}
