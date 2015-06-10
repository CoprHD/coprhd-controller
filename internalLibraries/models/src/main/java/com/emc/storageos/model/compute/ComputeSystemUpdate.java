/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.valid.Length;
import com.emc.storageos.model.valid.Range;

@XmlRootElement(name = "compute_system_update")
public class ComputeSystemUpdate {

	private String name;
    private String ipAddress;
    private Integer portNumber;
    private String userName;
    private String password;
    private String osInstallNetwork;
    private Boolean useSSL;

    public ComputeSystemUpdate() {}
    
	@Length(min = 2, max = 128)
	@XmlElement
    public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	
	@XmlElement(name = "ip_address")
	@JsonProperty("ip_address")
    public String getIpAddress() {
		return ipAddress;
	}
	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}
	
	@XmlElement(name = "port_number")
	@Range(min=1,max=65535)
	@JsonProperty("port_number")
    public Integer getPortNumber() {
		return portNumber;
	}
	public void setPortNumber(Integer portNumber) {
		this.portNumber = portNumber;
	}
    /**
     * Specifies whether to use SSL (Secure Sockets Layer) 
     * as the authentication method.
     * @valid none 
     */     
    @XmlElement(name = "use_ssl")
    public Boolean getUseSSL() {
        return useSSL;
    }

    public void setUseSSL(Boolean useSSL) {
        this.useSSL = useSSL;
    }	
	@XmlElement(name = "user_name")
	@JsonProperty("user_name")
    public String getUserName() {
		return userName;
	}
	public void setUserName(String userName) {
		this.userName = userName;
	}
	
	@XmlElement()
    public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}

	@XmlElement(name = "os_install_network",nillable=true)
	@JsonProperty("os_install_network")
	public String getOsInstallNetwork() {
		return osInstallNetwork;
	}

	public void setOsInstallNetwork(String osInstallNetwork) {
		this.osInstallNetwork = osInstallNetwork;
	}	
}
