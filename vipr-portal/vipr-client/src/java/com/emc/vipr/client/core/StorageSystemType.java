/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.vipr.client.core;

import com.emc.storageos.model.storagesystem.type.StorageSystemTypeList;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

public class StorageSystemType {

    private RestClient client;

    public StorageSystemType(RestClient client) {
        this.client = client;
    }

    public StorageSystemTypeRestRep getStorageSystemTypeRestRep(String uuid) {
        return client.get(StorageSystemTypeRestRep.class, PathConstants.STORAGE_SYSTEM_TYPE_URL + "/" + uuid);
    }

    public StorageSystemTypeList listStorageSystemTypes(String type) {
        return client.get(StorageSystemTypeList.class, PathConstants.STORAGE_SYSTEM_TYPE_URL + "/type/" + type);
    }

    public StorageSystemTypeList listRemoteReplicationTypes() {
        return client.get(StorageSystemTypeList.class, PathConstants.REMTOE_STORAGE_SYSTEM_TYPE_URL);
    }
}
