/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
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
