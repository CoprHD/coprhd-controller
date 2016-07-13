/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import com.emc.storageos.vnxe.models.VNXeNfsServer;

public class NfsServerListRequest extends KHRequests<VNXeNfsServer> {
    private static final String FIELDS = "hostName,fileInterfaces,nfsv4Enabled,nasServer";
	private static final String URL = "/api/types/nfsServer/instances";

    public NfsServerListRequest(KHClient client) {
        super(client);
        _url = URL;
	_fields = FIELDS;
    }

    public List<VNXeNfsServer> get() {
        return getDataForObjects(VNXeNfsServer.class);

    }
}
