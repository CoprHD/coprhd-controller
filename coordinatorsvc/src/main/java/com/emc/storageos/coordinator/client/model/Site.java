/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import java.util.HashMap;
import java.util.Map;

import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;

/**
 * Representation for a ViPR standby
 */
public class Site implements CoordinatorSerializable {

    public static final String CONFIG_KIND = "siteInfo";

    private static final String ENCODING_SEPERATOR = ",";
    private static final String ENCODING_MAP_KEYVALUE_SEPERATOR = ";";

    private String uuid;
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
    public String encodeAsString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(uuid).append(ENCODING_SEPERATOR);
        buffer.append(name).append(ENCODING_SEPERATOR);
        buffer.append(vip).append(ENCODING_SEPERATOR);
        buffer.append(secretKey).append(ENCODING_SEPERATOR);

        for (String key : hostIPv4AddressMap.keySet()) {
            buffer.append(key).append(ENCODING_MAP_KEYVALUE_SEPERATOR).append(hostIPv4AddressMap.get(key))
                    .append(ENCODING_MAP_KEYVALUE_SEPERATOR);
        }
        buffer.append(ENCODING_SEPERATOR);

        for (String key : hostIPv6AddressMap.keySet()) {
            buffer.append(key).append(ENCODING_MAP_KEYVALUE_SEPERATOR).append(hostIPv6AddressMap.get(key))
                    .append(ENCODING_MAP_KEYVALUE_SEPERATOR);
        }
        buffer.append(ENCODING_SEPERATOR);

        return buffer.toString();
    }

    @Override
    public Site decodeFromString(String infoStr) throws FatalCoordinatorException {
        if (infoStr == null) {
            return null;
        }

        String[] array = infoStr.split(ENCODING_SEPERATOR);

        Site site = new Site();
        site.setUuid(array[0]);
        site.setName(array[1]);
        site.setVip(array[2]);
        site.setSecretKey(array[3]);

        if (array.length >= 5) {
            convertString2Map(array[4], site.getHostIPv4AddressMap());
        }

        if (array.length >= 6) {
            convertString2Map(array[5], site.getHostIPv6AddressMap());
        }

        return site;
    }

    private void convertString2Map(String input, Map<String, String> map) {
        if (input != null && input.length() > 0) {
            String[] keyValues = input.split(ENCODING_MAP_KEYVALUE_SEPERATOR);
            for (int i = 0; i < keyValues.length; i++) {
                map.put(keyValues[i], keyValues[++i]);
            }
        }
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(this.uuid, CONFIG_KIND, "site");
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Site other = (Site) obj;
        if (uuid == null) {
            if (other.uuid != null)
                return false;
        } else if (!uuid.equals(other.uuid))
            return false;
        return true;
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