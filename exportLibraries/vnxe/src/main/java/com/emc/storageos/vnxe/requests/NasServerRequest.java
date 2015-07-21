/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;


import com.emc.storageos.vnxe.models.VNXeNasServer;


public class NasServerRequest extends KHRequests<VNXeNasServer>{
    private static final String URL = "/api/instances/nasServer/";
    public NasServerRequest(KHClient client, String id) {
        super(client);
        _url = URL + id;
    }


    public VNXeNasServer get(){
        return getDataForOneObject(VNXeNasServer.class);

    }

}
