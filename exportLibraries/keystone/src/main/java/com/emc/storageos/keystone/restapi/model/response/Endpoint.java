/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.keystone.restapi.model.response;

public class Endpoint
{
	private String adminURL;
	private String region;
	private String internalURL;
	private String id;
	private String publicURL;
	public String getAdminURL() {
		return adminURL;
	}
	public void setAdminURL(String adminURL) {
		this.adminURL = adminURL;
	}
	public String getRegion() {
		return region;
	}
	public void setRegion(String region) {
		this.region = region;
	}
	public String getInternalURL() {
		return internalURL;
	}
	public void setInternalURL(String internalURL) {
		this.internalURL = internalURL;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getPublicURL() {
		return publicURL;
	}
	public void setPublicURL(String publicURL) {
		this.publicURL = publicURL;
	}

}
