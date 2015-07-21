/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;


import com.sun.jersey.api.client.ClientResponse;

public class LogoutRequest extends KHRequests<ClientResponse>{
	private final static String URL = "/api/types/loginSessionInfo/action/logout";
	
	public LogoutRequest(KHClient client) {
		super(client);
		_url = URL;
	}
	

	public ClientResponse executeRequest() {
		return postRequest(null);
	}

}
