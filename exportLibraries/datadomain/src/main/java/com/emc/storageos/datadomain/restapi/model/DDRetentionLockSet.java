/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.datadomain.restapi.model;

import com.google.gson.Gson;

public class DDRetentionLockSet {
	
	// retention-lock enable: 0: disable; 1: enable; mode: governance | compliance.
    // Mode should be set when enable retention-lock-->
	private Boolean enable;	
	
	private String mode;
	
	public DDRetentionLockSet() {
		this.enable = false;
	}
	
	public DDRetentionLockSet(Boolean enable, String mode) {
		this.enable = enable;
		this.mode = mode;
	}

	public Boolean getEnable() {
		return enable;
	}

	public void setEnable(Boolean enable) {
		this.enable = enable;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public String toString() {
        return new Gson().toJson(this).toString();
    }

}
