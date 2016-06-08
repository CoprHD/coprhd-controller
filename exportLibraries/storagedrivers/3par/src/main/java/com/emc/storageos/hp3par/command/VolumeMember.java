/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class VolumeMember {
	private String id;
	private String name;
	private int provisioningType;
	private String copyType;
	private long baseId;
	private boolean readOnly;
	private String userCPG;
	
	public String getUserCPG() {
		return userCPG;
	}
	public void setUserCPG(String userCPG) {
		this.userCPG = userCPG;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getProvisioningType() {
		return provisioningType;
	}
	public void setProvisioningType(int provisioningType) {
		this.provisioningType = provisioningType;
	}
	public String getCopyType() {
		return copyType;
	}
	public void setCopyType(String copyType) {
		this.copyType = copyType;
	}
	public Long getBaseId() {
		return baseId;
	}
	public void setBaseId(long baseId) {
		this.baseId = baseId;
	}
	public boolean isReadOnly() {
		return readOnly;
	}
	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}
	public Integer getState() {
		return state;
	}
	public void setState(Integer state) {
		this.state = state;
	}
	public Long getSizeMiB() {
		return sizeMiB;
	}
	public void setSizeMiB(Long sizeMiB) {
		this.sizeMiB = sizeMiB;
	}
	public String getWwn() {
		return wwn;
	}
	public void setWwn(String wwn) {
		this.wwn = wwn;
	}
	private Integer state;
	private Long sizeMiB;
	private String wwn;
}
