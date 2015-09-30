/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.common;

import java.io.IOException;
import java.util.Map;

/**
 * Service configuration
 */
public interface Configuration {
    /**
     * Get configuration kind - For example, use "dbsvc" for all database configuration.
     * 
     * @return service name
     */
    public String getKind();

    /**
     * UUID of this configuration
     * 
     * @return service UUID
     */
    public String getId();
    
    /**
     * Retrieve configuration field
     * 
     * @param key configuration key
     * @return
     */
    public String getConfig(String key);

    /**
     * get all properties regardless of key
     * 
     * @param customOnly if set to true, filters out "kind", "keyid" and other built in
     *            properties
     * @return
     */
    public Map<String, String> getAllConfigs(boolean customOnly);

    /**
     * Sets configuration field
     * 
     * @param key config field key
     * @param val config val
     */
    public void setConfig(String key, String val);

    /**
     * Remove configuration field
     * 
     * @param key config field key
     */
    public void removeConfig(String key);

    /**
     * Serializes service information
     * 
     * @return
     */
    public byte[] serialize() throws IOException;
}
