/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;
import com.emc.storageos.vnxe.models.VNXeNasServer;
import com.emc.storageos.vnxe.models.VNXeStorageSystem;

public class NasServerListRequest extends KHRequests<VNXeNasServer> {
    private static final String URL = "/api/types/nasServer/instances";
	private static final String FIELDS = "name,isReplicationDestination";

    public NasServerListRequest(KHClient client) {
        super(client);
        _url = URL;
	_fields = FIELDS;
    }

    public List<VNXeNasServer> get() {
        return getDataForObjects(VNXeNasServer.class);

    }

}
