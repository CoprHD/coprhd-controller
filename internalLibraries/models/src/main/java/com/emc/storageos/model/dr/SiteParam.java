/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "site_sync")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteParam {
    private String uuid;
    private String name;
    private String vip;
    private String vip6;
    private String shortId;
    private Map<String, String> hostIPv4AddressMap;
    private Map<String, String> hostIPv6AddressMap;
    private int nodeCount;
    private String softwareVersion;
    private String dbSchemaVersion;
    private boolean freshInstallation;
    private String secretKey;
    private String state;
    private String ipsecKey;
    private long creationTime;
    private long dataRevision;

    @XmlElement(name = "uuid")
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "vip")
    public String getVip() {
        return vip;
    }

    public void setVip(String vip) {
        this.vip = vip;
    }
    
    @XmlElement(name = "vip6")
    public String getVip6() {
        return vip6;
    }

    public void setVip6(String vip6) {
        this.vip6 = vip6;
    }

    @XmlElement(name = "secret_key")
    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    @XmlElement(name = "host_ipv4_addressmap")
    public Map<String, String> getHostIPv4AddressMap() {
        return hostIPv4AddressMap;
    }

    public void setHostIPv4AddressMap(Map<String, String> hostIPv4AddressMap) {
        this.hostIPv4AddressMap = hostIPv4AddressMap;
    }

    @XmlElement(name = "host_ipv6_addressmap")
    public Map<String, String> getHostIPv6AddressMap() {
        return hostIPv6AddressMap;
    }

    public void setHostIPv6AddressMap(Map<String, String> hostIPv6AddressMap) {
        this.hostIPv6AddressMap = hostIPv6AddressMap;
    };
    
    @XmlElement(name = "software_version")
    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    @XmlElement(name = "dbschema_version")
    public String getDbSchemaVersion() {
        return dbSchemaVersion;
    }

    public void setDbSchemaVersion(String dbSchemaVersion) {
        this.dbSchemaVersion = dbSchemaVersion;
    }

    @XmlElement(name = "fresh_installation")
    public boolean isFreshInstallation() {
        return freshInstallation;
    }

    public void setFreshInstallation(boolean freshInstallation) {
        this.freshInstallation = freshInstallation;
    }

    @XmlElement(name = "short_id", required = false, nillable = true)
    public String getShortId() {
        return shortId;
    }

    public void setShortId(String shortId) {
        this.shortId = shortId;
    }

    @XmlElement(name = "state")
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @XmlElement(name = "ipsec_key")
    public String getIpsecKey() {
        return this.ipsecKey;
    }

    public void setIpsecKey(String ipsecKey) {
        this.ipsecKey = ipsecKey;
    }

    @XmlElement(name = "node_count")
    public int getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(int nodeCount) {
        this.nodeCount = nodeCount;
    }

    @XmlElement(name = "creationTime")
    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    @XmlElement(name = "data_revision")
    public long getDataRevision() {
        return dataRevision;
    }

    public void setDataRevision(long dataRevision) {
        this.dataRevision = dataRevision;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SiteParam [uuid=");
        builder.append(uuid);
        builder.append(", name=");
        builder.append(name);
        builder.append(", shortid=");
        builder.append(shortId);
        builder.append(", state=");
        builder.append(state);
        builder.append(", vip=");
        builder.append(vip);
        builder.append(", node count=");
        builder.append(nodeCount);
        builder.append(", hostIPv4AddressMap=");
        builder.append(hostIPv4AddressMap);
        builder.append(", hostIPv6AddressMap=");
        builder.append(hostIPv6AddressMap);
        builder.append(", softwareVersion=");
        builder.append(softwareVersion);
        builder.append(", dbSchemaVersion=");
        builder.append(dbSchemaVersion);
        builder.append(", freshInstallation=");
        builder.append(freshInstallation);
        builder.append(", creationTime=");
        builder.append(creationTime);
        builder.append(", dataRevision=");
        builder.append(dataRevision);
        builder.append("]");
        return builder.toString();
    }
}
