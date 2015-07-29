/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;
import com.emc.storageos.vnxe.models.VNXeNasServer;

public class NasServerListRequest extends KHRequests<VNXeNasServer> {
    private static final String URL = "/api/types/nasServer/instances";

    public NasServerListRequest(KHClient client) {
        super(client);
        _url = URL;
    }

    public List<VNXeNasServer> get() {
        return getDataForObjects(VNXeNasServer.class);

    }

}
