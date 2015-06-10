/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

@XmlRootElement(name = "os_install")
public class OsInstallParam {

	private URI computeImage;
	private URI volume;
	private String hostName;
	private String hostIp;
	private String netmask;
	private String gateway;
	private String ntpServer;
	private String dnsServers;
	private String managementNetwork;
	private boolean forceInstallation;
    private String rootPassword;

	public OsInstallParam() {
	}

	@XmlElement(name = "compute_image", required = true)
	@JsonProperty("compute_image")
	public URI getComputeImage() {
		return computeImage;
	}

	public void setComputeImage(URI computeImage) {
		this.computeImage = computeImage;
	}

	@XmlElement(name = "volume")
	@JsonProperty("volume")
	public URI getVolume() {
		return volume;
	}

	public void setVolume(URI volume) {
		this.volume = volume;
	}

	@XmlElement(name = "host_name")
	@JsonProperty("host_name")
	public String getHostName() {
		return hostName;
	}

	public void setHostName(String hostName) {
		this.hostName = hostName;
	}

	@XmlElement(name = "host_ip")
	@JsonProperty("host_ip")
	public String getHostIp() {
		return hostIp;
	}

	public void setHostIp(String hostIp) {
		this.hostIp = hostIp;
	}

	@XmlElement(name = "netmask")
	@JsonProperty("netmask")
	public String getNetmask() {
		return netmask;
	}

	public void setNetmask(String netmask) {
		this.netmask = netmask;
	}

	@XmlElement(name = "gateway")
	@JsonProperty("gateway")
	public String getGateway() {
		return gateway;
	}

	public void setGateway(String gateway) {
		this.gateway = gateway;
	}

	@XmlElement(name = "ntp_server")
	@JsonProperty("ntp_server")
	public String getNtpServer() {
		return ntpServer;
	}

	public void setNtpServer(String ntpServer) {
		this.ntpServer = ntpServer;
	}

	@XmlElement(name = "dns_servers")
	@JsonProperty("dns_servers")
	public String getDnsServers() {
		return dnsServers;
	}

	public void setDnsServers(String dnsServers) {
		this.dnsServers = dnsServers;
	}

	@XmlElement(name = "management_network")
	@JsonProperty("management_network")
	public String getManagementNetwork() {
		return managementNetwork;
	}

	public void setManagementNetwork(String managementNetwork) {
		this.managementNetwork = managementNetwork;
	}
	
	@XmlElement(name = "force_installation")
	@JsonProperty("force_installation")
	public boolean getForceInstallation() {
		return forceInstallation;
	}

	public void setForceInstallation(boolean forceInstallation) {
		this.forceInstallation = forceInstallation;
	}
	
    @XmlElement(name = "root_password", required = true)
    @JsonProperty("root_password")
    public String getRootPassword() {
        return rootPassword;
    }

    public void setRootPassword(String rootPassword) {
        this.rootPassword = rootPassword;
    }	
	
}
