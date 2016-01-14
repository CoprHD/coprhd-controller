/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geomodel;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class VdcPreCheckResponse {
    private URI id;
    private String connectionStatus;
    private Long version;
    private String shortId;
    private Integer hostCount;
    private HashMap<String, String> hostIPv4AddressesMap;
    private HashMap<String, String> hostIPv6AddressesMap;
    private String name;
    private String description;
    private String secretKey;
    private String apiEndpoint;
    private String softwareVersion;
    private boolean hasData = false;
    private boolean compatible = false;
    private boolean clusterStable;
    private String activeSiteId;
    
    // tenants which root has tenant role(s)
    private List<String> tenants;
    // projects which owned by root
    private List<String> projects;

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

    @XmlElement(name = "hostIPv4Addresses")
    public HashMap<String, String> getHostIPv4AddressesMap() {
        if (hostIPv4AddressesMap == null) {
            return new HashMap<String, String>();
        }

        return hostIPv4AddressesMap;
    }

    public void setHostIPv4AddressesMap(HashMap<String, String> addressesMap) {
        this.hostIPv4AddressesMap = addressesMap;
    }

    @XmlElement(name = "hostIPv6Addresses")
    public HashMap<String, String> getHostIPv6AddressesMap() {
        if (hostIPv6AddressesMap == null) {
            return new HashMap<String, String>();
        }

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

    @XmlElement(name = "api_endpoint")
    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }

    @XmlElement(name = "secret_key")
    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    @XmlElement(name = "software_version")
    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    @XmlElement(name = "compatible")
    public boolean getCompatible() {
        return compatible;
    }

    public void setCompatible(boolean compatible) {
        this.compatible = compatible;
    }

    @XmlElement(name = "hasData")
    public boolean isHasData() {
        return hasData;
    }

    public void setHasData(boolean hasData) {
        this.hasData = hasData;
    }

    @XmlElement(name = "clusterStable", required = true)
    public boolean isClusterStable() {
        return clusterStable;
    }

    public void setClusterStable(boolean clusterStable) {
        this.clusterStable = clusterStable;
    }

    @XmlElement(name = "tenants")
    public List<String> getTenants() {
        if (tenants == null) {
            tenants = new ArrayList<String>();
        }

        return tenants;
    }

    public void setTenants(List<String> tenants) {
        this.tenants = tenants;
    }

    @XmlElement(name = "projects")
    public List<String> getProjects() {
        if (projects == null) {
            projects = new ArrayList<String>();
        }

        return projects;
    }

    public void setProjects(List<String> projects) {
        this.projects = projects;
    }

    @XmlElement(name = "active_site_id")
    public String getActiveSiteId() {
        return activeSiteId;
    }

    public void setActiveSiteId(String activeSiteId) {
        this.activeSiteId = activeSiteId;
    }
    
}
