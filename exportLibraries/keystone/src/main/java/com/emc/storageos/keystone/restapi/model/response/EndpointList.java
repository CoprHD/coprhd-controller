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
import java.util.ArrayList;
import java.util.List;

public class EndpointList 
{
	private List <Endpoint> endpoints;

	public List <Endpoint> getEndpoints() {
	       if (endpoints == null) {
	    	   endpoints = new ArrayList<Endpoint>();
	        }
		return endpoints;
	}

	public void setEndpoints(List <Endpoint> endpoints) {
		this.endpoints = endpoints;
	}

}