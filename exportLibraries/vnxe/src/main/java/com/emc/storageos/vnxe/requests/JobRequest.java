/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import com.emc.storageos.vnxe.models.VNXeCommandJob;

public class JobRequest extends KHRequests<VNXeCommandJob>{
	private static final String URL = "/api/instances/job/";
    public JobRequest(KHClient client, String id) {
        super(client);
        _url = URL + id;
    }
    

    public VNXeCommandJob get(){
        return getDataForOneObject(VNXeCommandJob.class);

    }

}
