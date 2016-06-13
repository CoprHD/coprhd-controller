/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import com.emc.storageos.vnxe.models.VNXeFileInterface;

public class FileInterfaceListRequest extends KHRequests<VNXeFileInterface> {
    private static final String URL = "/api/types/fileInterface/instances";
    private static final String FIELDS = "name,nasServer,ipAddress";

    public FileInterfaceListRequest(KHClient client) {
        super(client);
        _url = URL;
        _fields = FIELDS;
    }

    public List<VNXeFileInterface> get() {
        return getDataForObjects(VNXeFileInterface.class);

    }
}
