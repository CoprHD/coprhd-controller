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

@XmlRootElement(name = "site_config")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteConfigRestRep extends SiteRestRep {
    private String softwareVersion;
    private String dbSchemaVersion;
    private boolean freshInstallation;
    private boolean isClusterStable;
    
    private String secretKey;
    private Map<String, String> hostIPv4AddressMap;
    private Map<String, String> hostIPv6AddressMap;
    private int nodeCount;
    private Map<String, Object> extraProperties;
    
    @XmlElement(name = "softwareVersion")
    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    @XmlElement(name = "dbSchemaVersion")
    public String getDbSchemaVersion() {
        return dbSchemaVersion;
    }

    public void setDbSchemaVersion(String dbSchemaVersion) {
        this.dbSchemaVersion = dbSchemaVersion;
    }

    @XmlElement(name = "freshInstallation")
    public boolean isFreshInstallation() {
        return freshInstallation;
    }

    public void setFreshInstallation(boolean freshInstallation) {
        this.freshInstallation = freshInstallation;
    }
    
    @XmlElement(name = "secretKey")
    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    @XmlElement(name = "hostIPv4AddressMap")
    public Map<String, String> getHostIPv4AddressMap() {
        return hostIPv4AddressMap;
    }    

    public void setHostIPv4AddressMap(Map<String, String> hostIPv4AddressMap) {
        this.hostIPv4AddressMap = hostIPv4AddressMap;
    }

    @XmlElement(name = "hostIPv6AddressMap")
    public Map<String, String> getHostIPv6AddressMap() {
        return hostIPv6AddressMap;
    }

    public void setHostIPv6AddressMap(Map<String, String> hostIPv6AddressMap) {
        this.hostIPv6AddressMap = hostIPv6AddressMap;
    }

    @XmlElement(name = "nodeCount")
    public int getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(int nodeCount) {
        this.nodeCount = nodeCount;
    }

    @XmlElement(name = "isClusterStable")
    public boolean isClusterStable() {
        return isClusterStable;
    }

    public void setClusterStable(boolean isClusterStable) {
        this.isClusterStable = isClusterStable;
    }

    @XmlElement(name = "extraProperties")
    public Map<String, Object> getExtraProperties() {
        return extraProperties;
    }

    public void setExtraProperties(Map<String, Object> extraProperties) {
        this.extraProperties = extraProperties;
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SiteRestRep [uuid=");
        builder.append(this.getUuid());
        builder.append(", name=");
        builder.append(this.getName());
        builder.append(", vip=");
        builder.append(this.getVip());
        builder.append(", hostIPv4AddressMap=");
        builder.append(this.getHostIPv4AddressMap());
        builder.append(", hostIPv6AddressMap=");
        builder.append(this.getHostIPv6AddressMap());
        builder.append(", softwareVersion=");
        builder.append(softwareVersion);
        builder.append(", dbSchemaVersion=");
        builder.append(dbSchemaVersion);
        builder.append(", freshInstallation=");
        builder.append(freshInstallation);
        builder.append("]");
        return builder.toString();
    }
}
