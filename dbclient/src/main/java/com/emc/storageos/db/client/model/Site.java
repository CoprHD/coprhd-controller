/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

/**
 * Representation for a ViPR standby
 */
@SuppressWarnings("serial")
@Cf("Site")
public class Site extends DataObject {

    private String uuid;
    private String name;
    private String vip;
    private String secretKey;
    private StringMap hostIPv4AddressMap = new StringMap();
    private StringMap hostIPv6AddressMap = new StringMap();

    @Name("uuid")
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
        setChanged("uuid");
    }

    @Name("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        setChanged("name");
    }

    @Name("vip")
    public String getVip() {
        return vip;
    }

    public void setVip(String vip) {
        this.vip = vip;
        setChanged("vip");
    }
    
    @Name("secretKey")
    @Encrypt
    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
        setChanged("secretKey");
    }

    @Name("hostIPv4AddressMap")
    public StringMap getHostIPv4AddressMap() {
        return hostIPv4AddressMap;
    }

    public void setHostIPv4AddressMap(StringMap hostIPv4AddressMap) {
        this.hostIPv4AddressMap = hostIPv4AddressMap;
        setChanged("hostIPv4AddressMap");
    }

    @Name("hostIPv6AddressMap")
    public StringMap getHostIPv6AddressMap() {
        return hostIPv6AddressMap;
    }

    public void setHostIPv6AddressMap(StringMap hostIPv6AddressMap) {
        this.hostIPv6AddressMap = hostIPv6AddressMap;
        setChanged("hostIPv6AddressMap");
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
        builder.append(", secretKey=");
        builder.append(secretKey);
        builder.append(", hostIPv4AddressMap=");
        builder.append(hostIPv4AddressMap);
        builder.append(", hostIPv6AddressMap=");
        builder.append(hostIPv6AddressMap);
        builder.append("]");
        return builder.toString();
    }
}
