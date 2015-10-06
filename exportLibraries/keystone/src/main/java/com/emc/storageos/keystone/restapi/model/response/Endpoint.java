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
