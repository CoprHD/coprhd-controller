/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@XmlRootElement
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
public class HostIpPortCreateParam extends ParamBase{
	private VNXeBase host;
	private String address;
	private String subnetMask;
	private boolean isIgnored = false;
	public VNXeBase getHost() {
		return host;
	}
	public void setHost(VNXeBase host) {
		this.host = host;
	}
	public String getAddress() {
		return address;
	}
	public void setAddress(String address) {
		this.address = address;
	}
	public String getSubnetMask() {
		return subnetMask;
	}
	public void setSubnetMask(String subnetMask) {
		this.subnetMask = subnetMask;
	}
	public boolean getIsIgnored() {
		return isIgnored;
	}
	public void setIsIgnored(boolean isIgnored) {
		this.isIgnored = isIgnored;
	}
	
	
}
