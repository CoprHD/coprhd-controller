/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;

/**
 * Representation for a ViPR site, both primary and standby
 */
public class Site implements CoordinatorSerializable {

    public static final String CONFIG_ID = "global";
    public static final String CONFIG_KIND = "siteInfo";

    private static final String ENCODING_SEPERATOR = ",";
    private static final String ENCODING_MAP_KEYVALUE_SEPERATOR = ";";
    
    private static final Logger log = LoggerFactory.getLogger(Site.class);

    private String uuid;
    private URI vdc;
    private String name;
    private String vip;
    private String secretKey;
    private String description;
    private Map<String, String> hostIPv4AddressMap = new HashMap<String, String>();
    private Map<String, String> hostIPv6AddressMap = new HashMap<String, String>();
    private String standbyShortId;
    private long creationTime;
    
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStandbyShortId() {
        return standbyShortId;
    }

    public void setStandbyShortId(String standbyShortId) {
        this.standbyShortId = standbyShortId;
    }
    
    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    @Override
    public String encodeAsString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(uuid).append(ENCODING_SEPERATOR);
        buffer.append(vdc).append(ENCODING_SEPERATOR);
        buffer.append(name).append(ENCODING_SEPERATOR);
        buffer.append(vip).append(ENCODING_SEPERATOR);
        buffer.append(secretKey).append(ENCODING_SEPERATOR);
        buffer.append(description).append(ENCODING_SEPERATOR);
        buffer.append(standbyShortId).append(ENCODING_SEPERATOR);
        buffer.append(creationTime).append(ENCODING_SEPERATOR);

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
        Site site;
        try {
            String[] array = infoStr.split(ENCODING_SEPERATOR);

            int index = 0;
            site = new Site();
            site.setUuid(array[index++]);
            site.setVdc(new URI(array[index++]));
            site.setName(array[index++]);
            site.setVip(array[index++]);
            site.setSecretKey(array[index++]);
            site.setDescription(array[index++]);
            site.setStandbyShortId(array[index++]);
            site.setCreationTime(Long.parseLong(array[index++]));

            if (index < array.length) {
                convertString2Map(array[index++], site.getHostIPv4AddressMap());
            }

            if (index < array.length) {
                convertString2Map(array[index++], site.getHostIPv6AddressMap());
            }
        } catch (Exception e) {
            log.error("Cant't decode String {} to Site, {}", infoStr, e);
            throw CoordinatorException.fatals.decodingError(e.getMessage());
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
        return new CoordinatorClassInfo(CONFIG_ID, CONFIG_KIND, "site", true);
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
        if (obj instanceof Site)
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
        builder.append(", vdc=");
        builder.append(vdc);
        builder.append(", name=");
        builder.append(name);
        builder.append(", vip=");
        builder.append(vip);
        builder.append(", description=");
        builder.append(description);
        builder.append(", hostIPv4AddressMap=");
        builder.append(hostIPv4AddressMap);
        builder.append(", hostIPv6AddressMap=");
        builder.append(hostIPv6AddressMap);
        builder.append(", standbyShortId=");
        builder.append(standbyShortId);
        builder.append(", creationTime=");
        builder.append(creationTime);
        builder.append("]");
        return builder.toString();
    }
}