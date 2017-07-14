/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VMAXApiClient{
	private static Logger logger = LoggerFactory.getLogger(VMAXApiClient.class);

    private URI baseURI;

    // The REST client for executing requests to the Unisphere.
    private RESTClient client;	
    
    public VMAXApiClient(URI baseURI, RESTClient client) {
    	this.baseURI = baseURI;
    	this.client = client;
	}
	
}
