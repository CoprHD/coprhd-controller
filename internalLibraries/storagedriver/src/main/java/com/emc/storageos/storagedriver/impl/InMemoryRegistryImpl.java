/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.storagedriver.Registry;

/**
 * In memory implementation of Registry.
 * Can be used for driver verification/testing.
 */

public class InMemoryRegistryImpl implements Registry {

    private Map<String, Map<String, Map<String, List<String>>>> registry = new HashMap<>();

    @Override
    public  synchronized void setDriverAttributesForKey(String driverName, String key, Map<String, List<String>> attributes) {

        Map<String, Map<String, List<String>>> driverAttributes = registry.get(driverName);
        if (driverAttributes == null) {
            driverAttributes = new HashMap<>();
            registry.put(driverName, driverAttributes);
        }
        driverAttributes.put(key, attributes);
    }

    @Override
    public synchronized void addDriverAttributeForKey(String driverName, String key, String attribute, List<String> value) {

        Map<String, Map<String, List<String>>> driverAttributes = registry.get(driverName);
        if (driverAttributes == null) {
            driverAttributes = new HashMap<>();
            registry.put(driverName, driverAttributes);
        }
        Map<String, List<String>>  driverAttributesForKey = driverAttributes.get(key);
        if (driverAttributes.get(key) == null) {
            driverAttributesForKey = new HashMap<>();
            driverAttributes.put(key, driverAttributesForKey);
        }

        driverAttributesForKey.put(attribute, value);
    }

    @Override
    public synchronized Map<String, List<String>> getDriverAttributesForKey(String driverName, String key) {

        Map<String, List<String>> keyAttributes = null;
        Map<String, Map<String, List<String>>> driverAttributes = registry.get(driverName);
        if (driverAttributes != null) {
            keyAttributes = driverAttributes.get(key);
        }
        return keyAttributes;
    }

    @Override
    public synchronized Map<String, Map<String, List<String>>> getDriverAttributes(String driverName) {
        return registry.get(driverName);
    }

    @Override
    public synchronized void clearDriverAttributes(String driverName) {

        registry.remove(driverName);
    }

    @Override
    public synchronized void clearDriverAttributesForKey(String driverName, String key) {
        Map<String, Map<String, List<String>>> driverAttributes = registry.get(driverName);
        if (driverAttributes != null) {
            driverAttributes.remove(key);
        }
    }

    @Override
    public synchronized void clearDriverAttributeForKey(String driverName, String key, String attribute) {
        Map<String, Map<String, List<String>>> driverAttributes = registry.get(driverName);
        if (driverAttributes != null) {
            Map<String, List<String>> driverAttributesForKey = driverAttributes.get(key);
            if (driverAttributesForKey != null) {
                driverAttributesForKey.remove(attribute);
            }
        }
    }

}
