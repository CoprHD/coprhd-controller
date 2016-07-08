/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.driver.ibmsvcdriver.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.storagedriver.Registry;

public class IBMSVCRegistry implements Registry {

    private Map<String, Map<String, Map<String, List<String>>>> registryData = null;
    private Map<String, Map<String, List<String>>> registryKeyData = null;
    private Map<String, List<String>> registryAttributeKeyData = null;
    
    public IBMSVCRegistry() {
        super();
        registryData = new HashMap<String, Map<String, Map<String, List<String>>>>();
        registryKeyData = new HashMap<String, Map<String, List<String>>>();
        registryAttributeKeyData = new HashMap<String, List<String>>();
    }

    @Override
    public void setDriverAttributesForKey(String driverName, String key, Map<String, List<String>> attributes) {
        registryKeyData.put(key, attributes);
        registryData.put(driverName, registryKeyData);
    }

    @Override
    public void addDriverAttributeForKey(String driverName, String key, String attribute, List<String> value) {
        registryAttributeKeyData.put(attribute, value);
        registryKeyData.put(key, registryAttributeKeyData);
        registryData.put(driverName, registryKeyData);
    }

    @Override
    public Map<String, List<String>> getDriverAttributesForKey(String driverName, String key) {
        return registryData.get(driverName).get(key);
    }

    @Override
    public Map<String, Map<String, List<String>>> getDriverAttributes(String driverName) {
        return registryData.get(driverName);
    }

    @Override
    public void clearDriverAttributes(String driverName) {
        registryData.get(driverName).clear();
    }

    @Override
    public void clearDriverAttributesForKey(String driverName, String key) {
        registryData.get(driverName).get(key).clear();
    }

    @Override
    public void clearDriverAttributeForKey(String driverName, String key, String attribute) {
        registryData.get(driverName).get(key).get(attribute).clear();
    }

}
