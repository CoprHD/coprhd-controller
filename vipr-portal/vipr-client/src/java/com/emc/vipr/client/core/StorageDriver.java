/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import com.emc.storageos.model.storagedriver.StorageDriverList;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

public class StorageDriver {

    private RestClient client;
    public StorageDriver(RestClient client) {
        this.client = client;
    }

    public StorageDriverList getDrivers() {
        return client.get(StorageDriverList.class, PathConstants.STORAGE_DRIVER_URL);
    }
}
