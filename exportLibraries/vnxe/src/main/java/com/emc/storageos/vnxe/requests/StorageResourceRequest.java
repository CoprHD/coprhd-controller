/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import com.emc.storageos.vnxe.models.StorageResource;

public class StorageResourceRequest extends KHRequests<StorageResource>{
    private static final String URL = "/api/instances/storageResource/";
    public StorageResourceRequest(KHClient client) {
        super(client);
        
    }


    public StorageResource get(String id){
        _url = URL + id;
        return getDataForOneObject(StorageResource.class);

    }

}
