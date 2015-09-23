/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Representation for a ViPR standby
 */
public class Site {

    private String uuid;
    private URI vdc;
    private String name;
    private String vip;
    private String secretKey;
    private Map<String, String> hostIPv4AddressMap = new HashMap<String, String>();
    private Map<String, String> hostIPv6AddressMap = new HashMap<String, String>();
    
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public URI getVdc() {
        return vdc;
    }

    public void setVdc(URI vdc) {
        this.vdc = vdc;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVip() {
        return vip;
    }

    public void setVip(String vip) {
        this.vip = vip;
    }
    
    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public Map<String, String> getHostIPv4AddressMap() {
        return hostIPv4AddressMap;
    }

    public void setHostIPv4AddressMap(Map<String, String> hostIPv4AddressMap) {
        this.hostIPv4AddressMap = hostIPv4AddressMap;
    }

    public Map<String, String> getHostIPv6AddressMap() {
        return hostIPv6AddressMap;
    }

    public void setHostIPv6AddressMap(Map<String, String> hostIPv6AddressMap) {
        this.hostIPv6AddressMap = hostIPv6AddressMap;
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Site [uuid=");
        builder.append(uuid);
        builder.append(", name=");
        builder.append(name);
        builder.append(", vip=");
        builder.append(vip);
        builder.append(", hostIPv4AddressMap=");
        builder.append(hostIPv4AddressMap);
        builder.append(", hostIPv6AddressMap=");
        builder.append(hostIPv6AddressMap);
        builder.append("]");
        return builder.toString();
    }
}
