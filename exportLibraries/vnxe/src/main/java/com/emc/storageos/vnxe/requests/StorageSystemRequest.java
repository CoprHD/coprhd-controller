/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.requests;

import java.util.List;

import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.VNXeStorageSystem;

public class StorageSystemRequest extends KHRequests<VNXeStorageSystem> {
    private static final String URL = "/api/types/system/instances";
    private static final String FIELDS = "serialNumber,name,model";

    public StorageSystemRequest(KHClient client) {
        super(client);
        _url = URL;
        _fields = FIELDS;
    }

    /*
     * get vnxe storage system
     * 
     * @param resource WebResource
     * 
     * @param client vnxe client
     * 
     * @throws VnxeException unexpectedDataError
     */
    public VNXeStorageSystem get() throws VNXeException {
        List<VNXeStorageSystem> allSystems = getDataForObjects(VNXeStorageSystem.class);
        // we only expect to get one system.
        if (allSystems == null || allSystems.isEmpty()) {
            return null;
        }
        return allSystems.get(0);

    }

}
