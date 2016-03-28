/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.requests;

import java.util.List;

import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.BasicSystemInfo;

public class BasicSystemInfoRequest extends KHRequests<BasicSystemInfo> {
    private static final String URL = "/api/types/basicSystemInfo/instances";
	private static final String FIELDS = "model,name,apiVersion,earliestApiVersion,softwareVersion";

    public BasicSystemInfoRequest(KHClient client) {
        super(client);
        _url = URL;
	_fields = FIELDS;
    }

    /*
     * get vnxe basic system info
     * 
     * @param resource WebResource
     * 
     * @param client vnxe client
     * 
     * @throws VnxeException unexpectedDataError
     */
    public BasicSystemInfo get() throws VNXeException {
        List<BasicSystemInfo> allSystems = getDataForObjects(BasicSystemInfo.class);
        // we only expect to get one system.
        if (allSystems == null || allSystems.isEmpty()) {
            return null;
        }
        return allSystems.get(0);

    }

}
