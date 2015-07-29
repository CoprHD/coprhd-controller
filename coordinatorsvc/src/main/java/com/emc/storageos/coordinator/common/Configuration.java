/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
