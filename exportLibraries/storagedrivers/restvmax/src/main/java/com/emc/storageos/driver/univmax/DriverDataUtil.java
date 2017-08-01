/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax;

import com.emc.storageos.driver.univmax.rest.RestClient;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DriverDataUtil {

    private static final Logger LOG = LoggerFactory.getLogger(DriverDataUtil.class);
    private UniVmaxStorageDriver driver;
    private HashMap<String, RestClient> restClientMap;
    private HashMap<String, HashSet<String>> providerSystemsMap;

    DriverDataUtil(UniVmaxStorageDriver driver) {
        this.driver = driver;
        restClientMap = new HashMap<>();
        providerSystemsMap = new HashMap<>();
    }

    public String getDriverName() {
        return driver.getClass().getSimpleName();
    }

    public RestClient getRestClientByStorageSystemId(String storageSystemId) {
        // TODO: generate RestClient according to symmetrixId in driver registry.
        if (!restClientMap.containsKey(storageSystemId)) {
            throw new NoSuchElementException("Rest client is not found for storage system: " + storageSystemId);
        }

        return restClientMap.get(storageSystemId);
    }

    public void addRestClient(String storageSystemId, RestClient restClient) {
        // TODO: record storageSystemId to match driver registry.
        restClientMap.put(storageSystemId, restClient);
    }

    /**
     * Track storage provider and the managed storage system(s).
     * If the set of managed systems changes, it also refreshes the rest client hashmap
     * by removing the unmanaged storage system(s).
     *
     * @param provider Storage provider.
     * @param systems Current list of storage system(s) managed by this storage provider.
     */
    public void setStorageProvider(StorageProvider provider, List<StorageSystem> systems) {
        // Calculate delta of old and new storage systems.
        String name = String.format("%s:%d:%s",
                provider.getProviderHost(), provider.getPortNumber(), provider.getUsername());
        HashSet<String> set = new HashSet<>();
        for (StorageSystem system : systems) {
            set.add(system.getSystemName());
        }
        // Remove unmanaged storage system(s) from the rest client hashmap.
        HashSet<String> oldSet = providerSystemsMap.get(name);
        if (oldSet != null) {
            oldSet.removeAll(set);
            for (String s : oldSet) {
                LOG.info("No longer manage storage system: {}", s);
                restClientMap.remove(s);
            }
        }

        // Store the new storage systems set.
        providerSystemsMap.put(name, set);
    }
}
