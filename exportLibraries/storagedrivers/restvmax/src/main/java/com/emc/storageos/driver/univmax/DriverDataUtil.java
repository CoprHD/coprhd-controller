/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax;

import com.emc.storageos.driver.univmax.rest.RestClient;
import com.sun.jersey.api.NotFoundException;

import java.util.HashMap;

public class DriverDataUtil {

    private UniVmaxStorageDriver driver;
    private HashMap<String, RestClient> clientHashMap;

    public DriverDataUtil(UniVmaxStorageDriver driver) {
        this.driver = driver;
        clientHashMap = new HashMap<>();
    }

    public String getDriverName() {
        return driver.getClass().getSimpleName();
    }

    public RestClient getRestClientByStorageSystemId(String storageSystemId) {
        // TODO: generate RestClient according to symmetrixId in driver registry.
        if (!clientHashMap.containsKey(storageSystemId)) {
            throw new NotFoundException("Rest client is not found for storage system: " + storageSystemId);
        }

        return clientHashMap.get(storageSystemId);
    }

    public void addRestClient(String storageSystemId, RestClient restClient) {
        // TODO: record storageSystemId to match driver registry.
        clientHashMap.put(storageSystemId, restClient);
    }
}
