/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import com.emc.storageos.vnxe.models.VNXeStorageProcessor;

public class StorageProcessorListRequest extends KHRequests<VNXeStorageProcessor> {
    private static final String URL = "/api/types/storageProcessor/instances";
    private static final String FIELDS = "name,emcSerialNumber,slotNumber";

    public StorageProcessorListRequest(KHClient client) {
        super(client);
        _url = URL;
	_fields = FIELDS;
    }

    public List<VNXeStorageProcessor> get() {
        return getDataForObjects(VNXeStorageProcessor.class);

    }

}
