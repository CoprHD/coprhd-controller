/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.installer.util;

import com.emc.storageos.services.util.InstallerConstants;

public enum ClusterType {
	NODE_COUNT_1 (1, InstallerConstants.NODE_COUNT_1_STRING),
	NODE_COUNT_3 (3, InstallerConstants.NODE_COUNT_3_STRING),
	NODE_COUNT_5 (5, InstallerConstants.NODE_COUNT_5_STRING);
	
	private int count;
	private String label;
	
	ClusterType(int count, String label) {
		this.count = count;
		this.label = label;
	}
	
	public int getCount() {
		return this.count;
	}
	public String getLabel() {
		return this.label;
	}
}
