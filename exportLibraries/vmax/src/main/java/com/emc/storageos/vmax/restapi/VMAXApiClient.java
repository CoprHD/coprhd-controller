/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

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

    public String getApiVersion() {
        // https://lglw7150.lss.emc.com:8443/univmax/restapi/system/version
        return "8.4.0.4";
    }

    public Set<String> getLocalStorageSystems() {
        Set<String> localSystems = new HashSet<>();
        localSystems.add("000196701343");
        localSystems.add("000196800794");
        localSystems.add("000196801468");
        return localSystems;
    }
}
