/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

public class VolumeAncestorInfo {	
	private String name;	
	private Integer copyType;
	private Long baseId;
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
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
}
