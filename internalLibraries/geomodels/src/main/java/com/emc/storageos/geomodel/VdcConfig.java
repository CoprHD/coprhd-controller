/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geomodel;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.PROPERTY)
public class VdcConfig {
    private URI id;
    private String connectionStatus;
    private Long version;
    private String shortId;
    private Integer hostCount;
    private String repStatus;

    private HashMap<String, String> hostIPv4AddressesMap = new HashMap<>();
    private HashMap<String, String> hostIPv6AddressesMap = new HashMap<>();

    private String name;
    private String description;
    private String secretKey;
    private String apiEndpoint;
    private String certificate_chain;

    private String geoCommandEndpoint;
    private String geoDataEndpoint;
    
    private String activeSiteId;

    private String ipsecKey;

    public static enum ConfigChangeType {
        CONNECT_VDC,
        REMOVE_VDC,
        UPDATE_VDC,
        DISCONNECT_VDC,
        RECONNECT_VDC
    };

    @XmlElement(name = "id")
    public URI getId() {
        return id;
    }

    public void setId(URI id) {
        this.id = id;
    }

    @XmlElement(name = "connection_status")
    public String getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(String connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    @XmlElement(name = "version")
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @XmlElement(name = "short_id")
    public String getShortId() {
        return shortId;
    }

    public void setShortId(String shortId) {
        this.shortId = shortId;
    }

    @XmlElement(name = "host_count")
    public Integer getHostCount() {
        return hostCount;
    }

    public void setHostCount(Integer hostCount) {
        this.hostCount = hostCount;
    }

    @XmlElement(name = "hostIPv4AddressesMap")
    public HashMap<String, String> getHostIPv4AddressesMap() {
        return hostIPv4AddressesMap;
    }

    public void setHostIPv4AddressesMap(HashMap<String, String> addressesMap) {
        this.hostIPv4AddressesMap = addressesMap;
    }

    @XmlElement(name = "hostIPv6AddressesMap")
    public HashMap<String, String> getHostIPv6AddressesMap() {
        return hostIPv6AddressesMap;
    }

    public void setHostIPv6AddressesMap(HashMap<String, String> addressesMap) {
        this.hostIPv6AddressesMap = addressesMap;
    }

    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement(name = "secret_key")
    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    @XmlElement(name = "api_endpoint")
    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }

    @XmlElement(name = "certificate_chain")
    public String getCertificateChain() {
        return certificate_chain;
    }

    public void setCertificateChain(String certificate_chain) {
        this.certificate_chain = certificate_chain;
    }

    @XmlElement(name = "geo_command_endpoint")
    public String getGeoCommandEndpoint() {
        return geoCommandEndpoint;
    }

    public void setGeoCommandEndpoint(String geoCommandEndpoint) {
        this.geoCommandEndpoint = geoCommandEndpoint;
    }

    @XmlElement(name = "geo_data_endpoint")
    public String getGeoDataEndpoint() {
        return geoDataEndpoint;
    }

    public void setGeoDataEndpoint(String geoDataEndpoint) {
        this.geoDataEndpoint = geoDataEndpoint;
    }

    @XmlElement(name = "rep_status")
    public String getRepStatus() {
        return repStatus;
    }

    public void setRepStatus(String repStatus) {
        this.repStatus = repStatus;
    }

    @XmlElement(name = "active_site_id")
    public String getActiveSiteId() {
        return activeSiteId;
    }

    public void setActiveSiteId(String activeSiteId) {
        this.activeSiteId = activeSiteId;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(this.getClass().getName());

        builder.append("\n\tshortId:");
        builder.append(getShortId());

        builder.append("\n\tIPv4 addresses Map:");
        builder.append(getHostIPv4AddressesMap());

        builder.append("\n\tIPv6 addresses Map:");
        builder.append(getHostIPv6AddressesMap());

        return builder.toString();
    }
}
