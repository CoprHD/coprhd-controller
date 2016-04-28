/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;


import java.util.List;
import java.util.Map;

public class CapabilityInstance {

    // Capability type of this capability instance
    private String capabilityDefinitionUid;

    // Capability name
    private String name;
    
    // Capability properties
    private Map<String, List<String>> properties;

    public CapabilityInstance(String capabilityDefinitionUid, String name, Map<String, List<String>> properties) {
        this.capabilityDefinitionUid = capabilityDefinitionUid;
        this.name = name;
        this.properties = properties;
    }

    public String getCapabilityDefinitionUid() {
        return capabilityDefinitionUid;
    }

    public String getName() {
        return name;
    }

    public Map<String, List<String>> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, List<String>> properties) {
        properties.putAll(properties);
    }

    public List<String> getProperty(String propertyName) {
        return properties.get(propertyName);
    }

    public void setProperty(String propertyName, List<String> propertyValue) {
        properties.put(propertyName, propertyValue);
    }
}
