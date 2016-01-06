/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.keystone.restapi.model.response;

public class User 
{
	private String username;
	private String roles_links[];
	private String id;
	private Role roles[];
	private String name;
	
	public String getUsername() {
		return username;
	}
	
	public void setUsername(String username) {
		this.username = username;
	}
	
	public String[] getRoles_links() {
		return roles_links;
	}
	
	public void setRoles_links(String[] roles_links) {
		this.roles_links = roles_links;
	}
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	
	public Role[] getRoles() {
		return roles;
	}
	
	public void setRoles(Role[] roles) {
		this.roles = roles;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}

}
