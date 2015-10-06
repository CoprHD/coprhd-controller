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

public class Service 
{
	private Endpoint endpoints[];
	private String endpoint_links[];
	private String type;
	private String name;

	public Endpoint[] getEndpoints() {
		return endpoints;
	}

	public void setEndpoints(Endpoint[] endpoints) {
		this.endpoints = endpoints;
	}

	public String[] getEndpoint_links() {
		return endpoint_links;
	}

	public void setEndpoint_links(String[] endpoint_links) {
		this.endpoint_links = endpoint_links;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}
