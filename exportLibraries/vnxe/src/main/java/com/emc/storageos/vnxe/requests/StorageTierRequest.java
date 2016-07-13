/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import com.emc.storageos.vnxe.models.VNXeStorageTier;

public class StorageTierRequest extends KHRequests<VNXeStorageTier> {
    private static final String URL = "/api/types/storageTier/instances";
    private static final String FIELDS = "sizeTotal";

    public StorageTierRequest(KHClient client) {
        super(client);
        _url = URL;
	_fields = FIELDS;
    }

    public List<VNXeStorageTier> get() {
        return getDataForObjects(VNXeStorageTier.class);

    }

}
