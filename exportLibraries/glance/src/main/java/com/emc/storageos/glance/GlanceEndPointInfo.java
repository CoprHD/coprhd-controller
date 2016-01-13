/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.glance;

import java.io.Serializable;

/**
 * Bean for Glance Endpoint Information. Use this bean for 
 * keeping glance details required to access the glance service.
 *
 */
public class GlanceEndPointInfo implements Serializable {
	
	private static final long serialVersionUID = -1311609353266088041L;
	private String glanceHostName = null;
	private String glanceRESTuserName = null;
	private String glanceRESTPassword = null;
	private String glanceRESTPort = null;
	private String glanceToken = null;
	private String glanceTenantId = null;
	private String glanceBaseUriHttp = null;
	private String glanceBaseUriHttps = null;
	private String glanceTenantName = null;
	private String baseUri = null;
	
	public GlanceEndPointInfo(String hostName, String userName,
			String password, String tenantName) {

		this.glanceHostName = hostName;
		this.glanceRESTuserName = userName;
		this.glanceRESTPassword = password;
		this.glanceTenantName = tenantName;
		this.glanceRESTPort = GlanceConstants.GLANCE_REST_PORT;
	}
	
	public String getGlanceTenantName() {
		return glanceTenantName;
	}


	public void setGlanceTenantName(String glanceTenantName) {
		this.glanceTenantName = glanceTenantName;
	}


	public String getGlanceHostName() {
		return glanceHostName;
	}

	public void setGlanceHostName(String glanceHostName) {
		this.glanceHostName = glanceHostName;
	}

	
	public String getGlanceRESTuserName() {
		return glanceRESTuserName;
	}

	public void setGlanceRESTuserName(String glanceRESTuserName) {
		this.glanceRESTuserName = glanceRESTuserName;
	}

	public String getGlanceRESTPassword() {
		return glanceRESTPassword;
	}

	public void setGlanceRESTPassword(String glanceRESTPassword) {
		this.glanceRESTPassword = glanceRESTPassword;
	}

	public String getGlanceRESTPort() {
		return glanceRESTPort;
	}

	public void setGlanceRESTPort(String glanceRESTPort) {
		this.glanceRESTPort = glanceRESTPort;
	}

	public String getGlanceToken() {
		return glanceToken;
	}

	public void setGlanceToken(String glanceToken) {
		this.glanceToken = glanceToken;
	}

	public String getGlanceTenantId() {
		return glanceTenantId;
	}

	public void setGlanceTenantId(String glanceTenantId) {
		this.glanceTenantId = glanceTenantId;
	}

	private String getGlanceBaseUriHttp() {
		return glanceBaseUriHttp;
	}

	public void setGlanceBaseUriHttp(String glanceBaseUriHttp) {
		this.glanceBaseUriHttp = glanceBaseUriHttp;
	}

	private String getGlanceBaseUriHttps() {
		return glanceBaseUriHttps;
	}

	public void setGlanceBaseUriHttps(String glanceBaseUriHttps) {
		this.glanceBaseUriHttps = glanceBaseUriHttps;
	}
	
	public String getBaseUri() {

		if(null == baseUri)
		{
			String endPointBaseUri = getGlanceBaseUriHttp();
			if (null == endPointBaseUri) {
				endPointBaseUri = getGlanceBaseUriHttps();
			}
			baseUri = endPointBaseUri.replace("5000/v2.0", GlanceConstants.GLANCE_REST_PORT);
		}		

		return baseUri;
	}


}
