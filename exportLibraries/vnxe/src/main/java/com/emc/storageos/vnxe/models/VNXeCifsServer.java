/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown=true)
public class VNXeCifsServer extends VNXeBase{
    private VNXeBase nasServer;
    private Health health;
    private List<String> smbProtocolVersions;
    private List<VNXeBase> fileInterfaces;
    private boolean smbcaSupported;
    private String name;
    private String netbiosName;
    private List<Integer> operationalStatus;
    private String domain;
    private String organizationUnit;
    private String workgroup;
    private boolean smbMultiChannelSupported;
    
    public VNXeBase getNasServer() {
        return nasServer;
    }
    public void setNasServer(VNXeBase nasServer) {
        this.nasServer = nasServer;
    }
    public List<VNXeBase> getFileInterfaces() {
        return fileInterfaces;
    }
    public void setFileInterfaces(List<VNXeBase> fileInterfaces) {
        this.fileInterfaces = fileInterfaces;
    }
	public Health getHealth() {
		return health;
	}
	public void setHealth(Health health) {
		this.health = health;
	}
	public List<String> getSmbProtocolVersions() {
		return smbProtocolVersions;
	}
	public void setSmbProtocolVersions(List<String> smbProtocolVersions) {
		this.smbProtocolVersions = smbProtocolVersions;
	}
	public boolean getSmbcaSupported() {
		return smbcaSupported;
	}
	public void setSmbcaSupported(boolean smbcaSupported) {
		this.smbcaSupported = smbcaSupported;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getNetbiosName() {
		return netbiosName;
	}
	public void setNetbiosName(String netbiosName) {
		this.netbiosName = netbiosName;
	}
	public List<Integer> getOperationalStatus() {
		return operationalStatus;
	}
	public void setOperationalStatus(List<Integer> operationalStatus) {
		this.operationalStatus = operationalStatus;
	}
	public String getDomain() {
		return domain;
	}
	public void setDomain(String domain) {
		this.domain = domain;
	}
	public String getOrganizationUnit() {
		return organizationUnit;
	}
	public void setOrganizationUnit(String organizationUnit) {
		this.organizationUnit = organizationUnit;
	}
	public String getWorkgroup() {
		return workgroup;
	}
	public void setWorkgroup(String workgroup) {
		this.workgroup = workgroup;
	}
	public boolean getSmbMultiChannelSupported() {
		return smbMultiChannelSupported;
	}
	public void setSmbMultiChannelSupported(boolean smbMultiChannelSupported) {
		this.smbMultiChannelSupported = smbMultiChannelSupported;
	}
    
    
}
