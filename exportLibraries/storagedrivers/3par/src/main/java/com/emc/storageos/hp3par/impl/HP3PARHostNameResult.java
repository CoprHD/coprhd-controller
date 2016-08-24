/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.impl;

public class HP3PARHostNameResult {
	private String hostName;
	private Boolean allInitiators;
	
	public String getHostName() {
		return hostName;
	}
	public void setHostName(String hostName) {
		this.hostName = hostName;
	}
	public Boolean getAllInitiators() {
		return allInitiators;
	}
	public void setAllInitiators(Boolean allInitiators) {
		this.allInitiators = allInitiators;
	}
}
