/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import com.emc.storageos.vnxe.models.VNXeHost;

public class HostRequest extends KHRequests<VNXeHost> {
    private static final String URL = "/api/instances/host/";
    private static final String FIELDS = "name,fcHostInitiators,iscsiHostInitiators";

    public HostRequest(KHClient client, String id) {
        super(client);
        _url = URL + id;
        _fields = FIELDS;
    }

    public VNXeHost get() {
        return getDataForOneObject(VNXeHost.class);

    }

}
