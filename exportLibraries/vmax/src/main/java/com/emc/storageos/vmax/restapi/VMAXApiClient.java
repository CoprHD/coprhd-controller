/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi;

import java.net.URI;

import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.SecurityUtils;
import com.emc.storageos.vmax.VMAXConstants;
import com.emc.storageos.vmax.restapi.model.response.NDMMigrationEnvironmentResponse;
import com.google.gson.Gson;
import com.sun.jersey.api.client.ClientResponse;

public class VMAXApiClient{
	private static Logger logger = LoggerFactory.getLogger(VMAXApiClient.class);

    private URI baseURI;

    // The REST client for executing requests to the Unisphere.
    private RESTClient client;	
    
    public VMAXApiClient(URI baseURI, RESTClient client) {
    	this.baseURI = baseURI;
    	this.client = client;
	}
    
    private <T> T getResponseObject(Class<T> clazz, ClientResponse response) throws Exception {
        JSONObject resp = response.getEntity(JSONObject.class);
        T respObject = new Gson().fromJson(SecurityUtils.sanitizeJsonString(resp.toString()), clazz);
        return respObject;
    }
	
    
    
    public NDMMigrationEnvironmentResponse getMigrationEnvironment(String sourceArraySerialNumber, String targetArraySerialNumber) throws Exception{
    	ClientResponse clientResponse = client.get(URI.create(VMAXConstants.VALIDATE_ENVIRONMENT_URI));
    	NDMMigrationEnvironmentResponse environmentResponse = getResponseObject(NDMMigrationEnvironmentResponse.class, clientResponse);
    	return environmentResponse;
    }
}
