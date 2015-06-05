/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vnxe.requests;

import java.util.List;

import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.VNXeStorageSystem;


public class StorageSystemRequest extends KHRequests<VNXeStorageSystem>{
	private static final String URL = "/api/types/system/instances";
    public StorageSystemRequest(KHClient client) {
        super(client);
        _url = URL;
    }

    /*
     * get vnxe storage system
     * @param resource WebResource
     * @param client vnxe client
     * @throws VnxeException unexpectedDataError
     */
    public VNXeStorageSystem get() throws VNXeException{
        List<VNXeStorageSystem> allSystems = getDataForObjects(VNXeStorageSystem.class);
        //we only expect to get one system.
        if (allSystems == null || allSystems.size() == 0) {
            return null;
        }
        return allSystems.get(0);
        
    }
    
    
}