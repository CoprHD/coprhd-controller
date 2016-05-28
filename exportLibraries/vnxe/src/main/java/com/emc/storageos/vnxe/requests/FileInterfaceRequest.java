/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import com.emc.storageos.vnxe.models.VNXeFileInterface;

public class FileInterfaceRequest extends KHRequests<VNXeFileInterface> {
    private static final String URL = "/api/instances/fileInterface/";
    private static final String FIELDS = "name,nasServer.id,ipAddress";


    public FileInterfaceRequest(KHClient client) {
        super(client);
        _url = URL;
	_fields = FIELDS;
    }

    public VNXeFileInterface get() {
        return getDataForOneObject(VNXeFileInterface.class);

    }
}
