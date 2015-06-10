/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.scaleio.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ScaleIOQueryAllResult {
    private ScaleIOAttributes properties;
    private Set<String> protectionDomains;
    private Map<String, Map<String, ScaleIOAttributes>> protectionDomainStoragePools;
    private Map<String, Long> protectionDomainToCapacity;

    public ScaleIOQueryAllResult() {
        this.properties = new ScaleIOAttributes();
        this.protectionDomains = new HashSet<String>();
        this.protectionDomainStoragePools = new HashMap<String, Map<String, ScaleIOAttributes>>();
    }

    public Set<String> getProtectionDomainNames() {
        return (protectionDomains != null) ? protectionDomains : Collections.EMPTY_SET;
    }

    public Set<String> getStoragePoolsForProtectionDomain(String protectDomainName) {
        return (protectionDomainStoragePools != null && protectionDomainStoragePools.containsKey(protectDomainName)) ?
                protectionDomainStoragePools.get(protectDomainName).keySet() : Collections.EMPTY_SET;
    }

    public String getProperty(String propertyName) {
        return properties.get(propertyName);
    }

    public Object getStoragePoolProperties(String protectionDomainName, String poolName) {
        ScaleIOAttributes result = null;
        if (protectionDomainStoragePools != null &&
                protectionDomainStoragePools.containsKey(protectionDomainName)) {
            Map<String, ScaleIOAttributes> poolToProperties = protectionDomainStoragePools.get(protectionDomainName);
            if (poolToProperties != null) {
                result = poolToProperties.get(poolName);
            }
        }
        return result;
    }

    public String getStoragePoolProperty(String protectionDomainName, String poolName, String propertyName) {
        String result = "";
        if (protectionDomainStoragePools != null &&
                protectionDomainStoragePools.containsKey(protectionDomainName)) {
            Map<String, ScaleIOAttributes> poolToProperties = protectionDomainStoragePools.get(protectionDomainName);
            if (poolToProperties != null) {
                result = poolToProperties.get(poolName).get(propertyName);
            }
        }
        return result;
    }

    void addProtectionDomain(String protectionDomainName) {
        protectionDomains.add(protectionDomainName);
    }

    void addProtectionDomainStoragePool(String protectionDomainName, String poolName) {
        Map<String, ScaleIOAttributes> poolMap = protectionDomainStoragePools.get(protectionDomainName);
        if (poolMap == null) {
            poolMap = new HashMap<String, ScaleIOAttributes>();
            protectionDomainStoragePools.put(protectionDomainName, poolMap);
        }
        poolMap.put(poolName, new ScaleIOAttributes());
    }

    void addStoragePoolProperty(String protectionDomainName, String poolName, String propertyName, String value) {
        if (protectionDomainStoragePools != null &&
                protectionDomainStoragePools.containsKey(protectionDomainName)) {
            Map<String, ScaleIOAttributes> poolToProperties = protectionDomainStoragePools.get(protectionDomainName);
            if (poolToProperties != null) {
                poolToProperties.get(poolName).put(propertyName, value);
            }
        }
    }

    void setProperty(String propertyName, String value) {
        properties.put(propertyName, value);
    }
}
