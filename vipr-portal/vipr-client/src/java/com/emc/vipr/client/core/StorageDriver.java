package com.emc.vipr.client.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

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

    public StorageSystemTypeAddParam upload(File f, String name) throws FileNotFoundException {
        return client.post(StorageSystemTypeAddParam.class, new FileInputStream(f),
                String.format(PathConstants.STORAGE_DRIVER_STORE_AND_PARSE_URL, name));
    }
}
