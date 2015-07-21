/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("ComputeImageJob")
public class ComputeImageJob extends DataObject {

	private URI computeImageId;
	private URI hostId;
	private URI volumeId;
	private String hostName;
	private String hostIp;
	private String passwordHash;
	private String netmask;
	private String gateway;
	private String ntpServer;
	private String dnsServers;
	private String managementNetwork;
	private String pxeBootIdentifier; // host UUID or MAC, must be unique
	private String bootDevice;
	private String jobStatus = JobStatus.CREATED.name();
	private Long jobStartTime;

	public static enum JobStatus {
		CREATED, SUCCESS, FAILED, TIMEDOUT
	}

	@RelationIndex(cf = "ComputeRelationIndex", type = ComputeImage.class)
	@Name("computeImageId")
	public URI getComputeImageId() {
		return computeImageId;
	}

	public void setComputeImageId(URI operatingSystemId) {
		this.computeImageId = operatingSystemId;
		setChanged("computeImageId");
	}

	@RelationIndex(cf = "ComputeRelationIndex", type = Host.class)
	@Name("hostId")
	public URI getHostId() {
		return hostId;
	}

	public void setHostId(URI hostId) {
		this.hostId = hostId;
		setChanged("hostId");
	}

	@Name("hostName")
	public String getHostName() {
		return hostName;
	}

	public void setHostName(String hostName) {
		this.hostName = hostName;
		setChanged("hostName");
	}

	@Name("hostIp")
	public String getHostIp() {
		return hostIp;
	}

	public void setHostIp(String hostIp) {
		this.hostIp = hostIp;
		setChanged("hostIp");
	}

	@Name("passwordHash")
    public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
		setChanged("passwordHash");
	}	
	
	@Name("netmask")
	public String getNetmask() {
		return netmask;
	}

	public void setNetmask(String netmask) {
		this.netmask = netmask;
		setChanged("netmask");
	}

	@Name("gateway")
	public String getGateway() {
		return gateway;
	}

	public void setGateway(String gateway) {
		this.gateway = gateway;
		setChanged("gateway");
	}

	@Name("ntpServer")
	public String getNtpServer() {
		return ntpServer;
	}

	public void setNtpServer(String ntpServer) {
		this.ntpServer = ntpServer;
		setChanged("ntpServer");
	}

	@Name("dnsServers")
	public String getDnsServers() {
		return dnsServers;
	}

	public void setDnsServers(String dnsServers) {
		this.dnsServers = dnsServers;
		setChanged("dnsServers");
	}

	@Name("managementNetwork")
	public String getManagementNetwork() {
		return managementNetwork;
	}

	public void setManagementNetwork(String managementNetwork) {
		this.managementNetwork = managementNetwork;
		setChanged("managementNetwork");
	}

	@Name("volumeId")
	public URI getVolumeId() {
		return volumeId;
	}

	@RelationIndex(cf = "ComputeRelationIndex", type = Volume.class)
	public void setVolumeId(URI volumeId) {
		this.volumeId = volumeId;
		setChanged("volumeId");
	}

	@Name("pxeBootIdentifier")
	@AlternateId("AltIdIndex")
	public String getPxeBootIdentifier() {
		return pxeBootIdentifier;
	}

	public void setPxeBootIdentifier(String pxeBootIdentifier) {
		this.pxeBootIdentifier = pxeBootIdentifier;
		setChanged("pxeBootIdentifier");
	}

	@Name("jobStatus")
	public String getJobStatus() {
		return jobStatus;
	}

	public void setJobStatus(String jobStatus) {
		this.jobStatus = jobStatus;
		setChanged("jobStatus");
	}

	@Name("bootDevice")
	public String getBootDevice() {
		return bootDevice;
	}

	public void setBootDevice(String bootDevice) {
		this.bootDevice = bootDevice;
		setChanged("bootDevice");
	}

	@Name("jobStartTime")
	public Long getJobStartTime() {
		return jobStartTime;
	}

	public void setJobStartTime(Long jobStartTime) {
		this.jobStartTime = jobStartTime;
		setChanged("jobStartTime");
	}
}
