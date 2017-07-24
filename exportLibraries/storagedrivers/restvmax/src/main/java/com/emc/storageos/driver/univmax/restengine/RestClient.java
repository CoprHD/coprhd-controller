/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.restengine;

import java.net.URI;
import java.util.Base64;

import javax.ws.rs.core.MediaType;

import com.emc.storageos.storagedriver.restutil.StandardRestClient;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;

/**
 * @author fengs5
 *
 */
public class RestClient extends StandardRestClient {
    private static final String AUTHORIZATION = "Authorization";
    private static final String BASIC = "Basic ";
    private static final String CONTENT_TYPE = "Content-Type";

    /**
     * 
     * @param username
     * @param password
     * @param client
     */
    public RestClient(String username, String password, Client client) {
        _client = client;
        _base = URI.create("");
        _username = username;
        _password = password;
        _authToken = "";
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.driver.vmax3.restengine.StandardRestClient#setResourceHeaders(com.sun.jersey.api.client.WebResource)
     */
    @Override
    protected Builder setResourceHeaders(WebResource resource) {
        return resource.header(CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION, getAuthFieldValue());
    }

    @Override
    protected void authenticate() {

    }

    private String getAuthFieldValue() {
        return BASIC
                + Base64.getEncoder()
                        .encodeToString((_username + ":" + _password).getBytes());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.driver.vmax3.restengine.StandardRestClient#checkResponse(java.net.URI,
     * com.sun.jersey.api.client.ClientResponse)
     */
    @Override
    protected int checkResponse(URI uri, ClientResponse response) {
        return 0;
    }

}
