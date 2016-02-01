/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.protection;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DiscoveredSystemObjectRestRep;

@XmlRootElement(name = "protection_system")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ProtectionSystemRestRep extends DiscoveredSystemObjectRestRep {
    private String installationId;
    private String majorVersion;
    private String minorVersion;
    private String ipAddress;
    private Integer portNumber;
    private Boolean reachableStatus;
    private String username;
    private List<ProtectionSystemRPClusterRestRep> rpClusters;

    public ProtectionSystemRestRep() {
    }

    /**
     * The Installation ID of this Protection System
     * 
     */
    @XmlElement(name = "installation_id")
    public String getInstallationId() {
        return installationId;
    }

    public void setInstallationId(String installationId) {
        this.installationId = installationId;
    }

    /**
     * IP Address of the Protection System device
     * 
     */
    @XmlElement(name = "ip_address")
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * The Major Version of the Protection System device
     * 
     */
    @XmlElement(name = "major_version")
    public String getMajorVersion() {
        return majorVersion;
    }

    public void setMajorVersion(String majorVersion) {
        this.majorVersion = majorVersion;
    }

    /**
     * The Minor Version of the Protection System device
     * 
     */
    @XmlElement(name = "minor_version")
    public String getMinorVersion() {
        return minorVersion;
    }

    public void setMinorVersion(String minorVersion) {
        this.minorVersion = minorVersion;
    }

    /**
     * Management Port Number of the Protection System device
     * 
     */
    @XmlElement(name = "port_number")
    public Integer getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this.portNumber = portNumber;
    }

    /**
     * Reachable Status which indicates that the Portection System is reachable
     * by ViPR doing a ping. (establish connection)
     * 
     */
    @XmlElement(name = "reachable")
    public Boolean getReachableStatus() {
        return reachableStatus;
    }

    public void setReachableStatus(Boolean reachableStatus) {
        this.reachableStatus = reachableStatus;
    }

    @XmlElement(name = "user_name")
    public String getUsername() {
        return username;
    }

    /**
     * The user name to connect to the Protection System device management port
     * 
     */
    public void setUsername(String username) {
        this.username = username;
    }

    @XmlElement(name = "clusters")
    public List<ProtectionSystemRPClusterRestRep> getRpClusters() {
        return rpClusters;
    }

    public void setRpClusters(List<ProtectionSystemRPClusterRestRep> rpClusters) {
        this.rpClusters = rpClusters;
    }
}
