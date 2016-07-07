/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class VolumeMember {
	private String id;
	private String name;
	private Integer provisioningType;
	private Integer copyType;
	private Long baseId;
	private Long physParentId;
	private boolean readOnly;
	private String userCPG;
	private String copyOf;	
	private Integer state;
	private Long sizeMiB;
	private String wwn;
	
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
	public int getCopyType() {
		return copyType;
	}
	public void setCopyType(int copyType) {
		this.copyType = copyType;
	}
	public long getBaseId() {
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
	public String getUserCPG() {
		return userCPG;
	}
	public void setUserCPG(String userCPG) {
		this.userCPG = userCPG;
	}
	public String getCopyOf() {
		return copyOf;
	}
	public void setCopyOf(String copyOf) {
		this.copyOf = copyOf;
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
	public Long getPhysParentId() {
		return physParentId;
	}
	public void setPhysParentId(Long physParentId) {
		this.physParentId = physParentId;
	}
	
}
