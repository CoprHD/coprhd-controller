package com.emc.storageos.storagedriver.impl;


import com.emc.storageos.storagedriver.Registry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegistryImpl implements Registry {

    private static Registry registry;
    // registry key map
    // key --- entity id
    // value --- name/value pairs for attributes associated to the key
    Map<String, Map<String, List<String>>> registryKeyMap = new HashMap<>();

    private RegistryImpl() {
    }

    public static Registry getInstance() {
        if (registry == null) {
            registry = new RegistryImpl();
        }

        return registry;
    }

    @Override
    public void setDriverAttributesForKey(String driverName, String key, Map<String, List<String>> attributes) {

    }

    @Override
    public Map<String, List<String>> getDriverAttributesForKey(String driverName, String key) {
        return null;
    }

    @Override
    public Map<String, Map<String, List<String>>> getDriverAttributes(String driverName) {
        return null;
    }

    @Override
    public void clearDriverAttributes(String driverName) {

    }

    @Override
    public void clearDriverAttributesForKey(String driverName, String key) {

    }

    @Override
    public void clearDriverAttributeForKey(String driverName, String key, String attribute) {

    }
}
