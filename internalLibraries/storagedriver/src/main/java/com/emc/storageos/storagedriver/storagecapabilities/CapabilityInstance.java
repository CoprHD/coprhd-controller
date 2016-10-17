/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;

import java.util.List;
import java.util.Map;

/**
 * Class encapsulates a specific storage capability as specified by its associated CapabilityDefinition
 */
public class CapabilityInstance {

    // The unique identifier for the capability definition of the capability instance.
    private String capabilityDefinitionUid;

    // Capability name
    private String name;
    
    // The values for the capability properties specified in the associated capability definition.
    private Map<String, List<String>> properties;

    /**
     * Constructor
     * 
     * @param capabilityDefinitionUid The unique identifier for the capability definition.
     * @param name The name for this capability instance.
     * @param properties The property values.
     */
    public CapabilityInstance(String capabilityDefinitionUid, String name, Map<String, List<String>> properties) {
        this.capabilityDefinitionUid = capabilityDefinitionUid;
        this.name = name;
        this.properties = properties;
    }

    /**
     * Getter for the UID of the associated capability definition.
     * 
     * @return The UID of the associated capability definition.
     */
    public String getCapabilityDefinitionUid() {
        return capabilityDefinitionUid;
    }

    /**
     * Getter for the capability name.
     * 
     * @return The capability name.
     */
    public String getName() {
        return name;
    }

    /**
     * Getter for the property values map.
     * 
     * @return The property values map.
     */
    public Map<String, List<String>> getProperties() {
        return properties;
    }
    
    /**
     * Setter for the property values map.
     * 
     * @param properties The property values map.
     */
    public void setProperties(Map<String, List<String>> properties) {
        this.properties.putAll(properties);
    }    

    /**
     * Gets the values for a specific property.
     * 
     * @param propertyName The property name.
     * 
     * @return The values for the passed property.
     */
    public List<String> getPropertyValues(String propertyName) {
        if (properties != null) {
            return properties.get(propertyName);
        } else {
            return null;
        }
    }
    
    /**
     * Sets the values for a specific property.
     * 
     * @param propertyName The property name.
     * @param propertyValues The values for the passed property.
     */
    public void setPropertyValues(String propertyName, List<String> propertyValues) {
        properties.put(propertyName, propertyValues);
    }

    /**
     * Convenience method to get the value for the given property that only
     * has a single value.
     * 
     * @param propertyName The name of the property
     * 
     * @return The single value for the specified property or null if not set.
     */
    public String getPropertyValue(String propertyName) {
        String propValue = null;
        if (properties != null) {
            List<String> propVals = properties.get(propertyName);
            if ((propVals != null) && (!propVals.isEmpty())) {
                propValue = propVals.get(0);
            }
        }
        return propValue;
    }
}
