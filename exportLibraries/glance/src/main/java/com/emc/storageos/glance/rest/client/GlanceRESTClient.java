/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.glance.rest.client;

import java.net.URI;

import javax.ws.rs.core.MediaType;

import com.emc.storageos.glance.GlanceConstants;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;


/**
 * GlanceRESTClient to invoke RESTful web service calls of 
 * OpenStack Glance service.Use this client to fetch any 
 * information from OpenStack Glance Service
 *
 */
public class GlanceRESTClient {
	
	private Client client;
	private String authTokenHeader = "";
	

	public GlanceRESTClient(Client jerseyClient) {
		this.client = jerseyClient;
	}
	
	public GlanceRESTClient(Client jerseyClient, String authToken) {
		this.client = jerseyClient;
		this.authTokenHeader = authToken;
	}
	
	public String getAuthTokenHeader() {
		return authTokenHeader;
	}

	public void setAuthTokenHeader(String authTokenHeader) {
		this.authTokenHeader = authTokenHeader;
	}
	
	/***
	 * Get the requested resource.
	 * @param uri
	 * @return
	 */
	public ClientResponse get(URI uri)
	{
		return client.resource(uri)
				.header(GlanceConstants.AUTH_TOKEN_HEADER, authTokenHeader)
				.get(ClientResponse.class);
	}
	
	/**
	 * Simple post query. No header
	 * provided.
	 * 
	 * @param uri
	 * @param body
	 * @return
	 */
	public ClientResponse post(URI uri, String body)
	{
		return client.resource(uri)
                .type(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, body);
	}
	
	
	/**
	 * Post query with header.
	 * 
	 * @param uri
	 * @param body
	 * @return
	 */
	public ClientResponse postWithHeader(URI uri, String body)
	{
		return client.resource(uri)
				.header(GlanceConstants.AUTH_TOKEN_HEADER, authTokenHeader)
                .type(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, body);
	}
	
	
	/**
     * Delete resource
     * @param   uri : uri for the resource to delete
     * @return  ClientResponse
     */
    public ClientResponse delete(URI uri) {
        return client.resource(uri)
        		.header(GlanceConstants.AUTH_TOKEN_HEADER, authTokenHeader)
        		.delete(ClientResponse.class);
    }
    
    /**
     *  Put resource at uri
     * @param   url             uri for the resource to put
     * @param   queryParams     if any parameters supplied
     * @param   body            body for the put
     * @return  ClientResponse  response
     */
    public ClientResponse put(URI uri, String body) {
    	
    	return client.resource(uri)
    			.header(GlanceConstants.AUTH_TOKEN_HEADER, authTokenHeader)
    			.put(ClientResponse.class, body);
        
    }
}
