package com.emc.storageos.storagedriver;

import java.util.List;
import java.util.Map;


public interface Registry {

    /**
     * Set attributes for a given driver and a given key.
     * This method will update existing attributes and add new attributes.
     * @param driverName
     * @param key
     * @param attributes
     */
    public void setDriverAttributesForKey(String driverName, String key, Map<String, List<String>> attributes);

    /**
     * Get all attributes for a given driver and a given key
     *
     * @param driverName
     * @param key
     * @return
     */
    public Map<String, List<String>> getDriverAttributesForKey(String driverName, String key);

    /**
     * Get all attributes for a given driver
     *
     * @param driverName
     * @return
     */
    public Map<String, Map<String, List<String>>> getDriverAttributes(String driverName);

    /**
     * Clear all driver attributes
     * @param driverName
     */
    public void clearDriverAttributes(String driverName);

    /**
     * Clear driver attributes for a given key
     * @param driverName
     * @param key
     */
    public void clearDriverAttributesForKey(String driverName, String key);

    /**
     * Clear specific driver attribute defined for a given key
     * @param driverName
     * @param key
     * @param attribute
     */
    public void clearDriverAttributeForKey(String driverName, String key, String attribute);



}
