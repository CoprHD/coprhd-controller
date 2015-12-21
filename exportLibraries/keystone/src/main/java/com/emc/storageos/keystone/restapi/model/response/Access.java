/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.keystone.restapi.model.response;

public class Access 
{
	private Token token;
	private Service serviceCatalog[];
	private User user;
	private Metadata metadata;
	
	public Token getToken() {
		return token;
	}
	
	public void setToken(Token token) {
		this.token = token;
	}
	
	public Service[] getServiceCatalog() {
		return serviceCatalog;
	}
	
	public void setServiceCatalog(Service[] serviceCatalog) {
		this.serviceCatalog = serviceCatalog;
	}
	
	public User getUser() {
		return user;
	}
	public void setUser(User user) {
		this.user = user;
	}
	
	public Metadata getMetadata() {
		return metadata;
	}
	
	public void setMetadata(Metadata metadata) {
		this.metadata = metadata;
	}
	

}
