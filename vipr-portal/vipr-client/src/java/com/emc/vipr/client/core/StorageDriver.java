package com.emc.vipr.client.core;

import com.emc.storageos.model.storagesystem.type.StorageSystemTypeAddParam;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

public class StorageDriver {

    protected final RestClient client;

    public StorageDriver(RestClient client) {
        this.client = client;
    }

    public void install(StorageSystemTypeAddParam input) {
        client.post(StorageSystemTypeRestRep.class, input, PathConstants.STORAGE_DRIVER_INSTALL_URL);
    }
}
