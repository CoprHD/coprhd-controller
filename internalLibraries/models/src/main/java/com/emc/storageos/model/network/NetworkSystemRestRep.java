/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import com.emc.storageos.model.DiscoveredSystemObjectRestRep;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "network_system")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class NetworkSystemRestRep extends DiscoveredSystemObjectRestRep {
    private String serialNumber;
    private String ipAddress;
    private String secondaryIP;
    private Integer portNumber;
    private String username;
    private String smisProviderIP;
    private Integer smisPortNumber;
    private String smisUserName;
    private Boolean smisUseSSL;
    private String version;
    private String uptime;

    public NetworkSystemRestRep() {
    }

    /**
     * The system's management IP address
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
     * The system's management port
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
     * The system's backup management IP address
     * 
     */
    @XmlElement(name = "secondary_ip")
    public String getSecondaryIP() {
        return secondaryIP;
    }

    public void setSecondaryIP(String secondaryIP) {
        this.secondaryIP = secondaryIP;
    }

    /**
     * The system's serial number
     * 
     */
    @XmlElement(name = "serial_number")
    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    /**
     * The SMI-S management port
     * 
     */
    @XmlElement(name = "smis_port_number")
    public Integer getSmisPortNumber() {
        return smisPortNumber;
    }

    public void setSmisPortNumber(Integer smisPortNumber) {
        this.smisPortNumber = smisPortNumber;
    }

    /**
     * The IP address of the SMI-S manager for this system
     * 
     */
    @XmlElement(name = "smis_provider_ip")
    public String getSmisProviderIP() {
        return smisProviderIP;
    }

    public void setSmisProviderIP(String smisProviderIP) {
        this.smisProviderIP = smisProviderIP;
    }

    /**
     * The login name for SMI-S management
     * 
     */
    @XmlElement(name = "smis_user_name")
    public String getSmisUserName() {
        return smisUserName;
    }

    public void setSmisUserName(String smisUserName) {
        this.smisUserName = smisUserName;
    }

    /**
     * Whether or not to use SSL when communicating with the SMI-S
     * manager
     * 
     */
    @XmlElement(name = "smis_use_ssl")
    public Boolean getSmisUseSSL() {
        return smisUseSSL;
    }

    public void setSmisUseSSL(Boolean smisUseSSL) {
        this.smisUseSSL = smisUseSSL;
    }

    /**
     * How long the system has been running
     * 
     */
    @XmlElement(name = "uptime")
    public String getUptime() {
        return uptime;
    }

    public void setUptime(String uptime) {
        this.uptime = uptime;
    }

    /**
     * The login name for managing the system through its console
     * 
     */
    @XmlElement(name = "user_name")
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * The system's software version
     * 
     */
    @XmlElement(name = "version")
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
