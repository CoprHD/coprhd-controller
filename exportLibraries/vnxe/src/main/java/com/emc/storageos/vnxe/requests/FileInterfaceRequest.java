/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import com.emc.storageos.vnxe.models.VNXeFileInterface;

public class FileInterfaceRequest extends KHRequests<VNXeFileInterface>{
    private static final String URL = "/api/instances/fileInterface/";
    public FileInterfaceRequest(KHClient client, String id) {
        super(client);
        _url = URL + id;
    }


    public VNXeFileInterface get(){
        return getDataForOneObject(VNXeFileInterface.class);

    }
}
