/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.keystone.restapi.model.response;

public class Metadata 
{
	private int is_admin;
	private String roles[];
	
	public int getIs_admin() {
		return is_admin;
	}
	
	public void setIs_admin(int is_admin) {
		this.is_admin = is_admin;
	}
	
	public String[] getRoles() {
		return roles;
	}
	
	public void setRoles(String[] roles) {
		this.roles = roles;
	}
		

}
