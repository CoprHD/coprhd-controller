/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DiscoveredSystemObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "compute_system")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ComputeSystemRestRep extends DiscoveredSystemObjectRestRep {
    private String ipAddress;
    private Integer portNumber;
    private String username;
    private String version;
    private String osInstallNetwork;
    private String vlans;
    private Boolean useSSL;
    private String computeImageServer;
    

    private List<NamedRelatedResourceRep> serviceProfileTemplates = new ArrayList<NamedRelatedResourceRep>();

    public ComputeSystemRestRep() {
    }

    @XmlElement(name = "ip_address")
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    @XmlElement(name = "port_number")
    public Integer getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this.portNumber = portNumber;
    }

    @XmlElement(name = "user_name")
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Whether or not secure SSL connection is used.
     * 
     * @valid true
     * @valid false
     */
    @XmlElement(name = "use_ssl")
    public Boolean getUseSSL() {
        return useSSL;
    }

    public void setUseSSL(Boolean useSSL) {
        this.useSSL = useSSL;
    }

    @XmlElement(name = "version")
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @XmlElementWrapper(name = "service_profile_templates", nillable = true, required = false)
    @XmlElement(name = "service_profile_template")
    public List<NamedRelatedResourceRep> getServiceProfileTemplates() {
        return serviceProfileTemplates;
    }

    public void setServiceProfileTemplates(
            List<NamedRelatedResourceRep> serviceProfileTemplates) {
        this.serviceProfileTemplates = serviceProfileTemplates;
    }

    @XmlElement(name = "os_install_network")
    public String getOsInstallNetwork() {
        return osInstallNetwork;
    }

    public void setOsInstallNetwork(String osInstallNetwork) {
        this.osInstallNetwork = osInstallNetwork;
    }

    @XmlElement(name = "vlans")
    public String getVlans() {
        return vlans;
    }

    public void setVlans(String vlans) {
        this.vlans = vlans;
    }

    @XmlElement(name = "compute_image_server")
	public String getComputeImageServer() {
		return computeImageServer;
	}

	public void setComputeImageServer(String computeImageServer) {
		this.computeImageServer = computeImageServer;
	}
    
    
}
