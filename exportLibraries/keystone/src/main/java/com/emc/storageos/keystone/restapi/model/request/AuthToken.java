/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.keystone.restapi.model.request;

import com.google.gson.Gson;

public class AuthToken 
{
	private String tenantName;
	
	private PassWordCredentials passwordCredentials;

	public String getTenantName() {
		return tenantName;
	}

	public void setTenantName(String tenantName) {
		this.tenantName = tenantName;
	}

	public PassWordCredentials getPasswordCreds() {
		return passwordCredentials;
	}

	public void setPasswordCreds(PassWordCredentials passwordCreds) {
		this.passwordCredentials = passwordCreds;
	}
	
	public String toString() {
        return new Gson().toJson(this).toString();
    }
	
}
