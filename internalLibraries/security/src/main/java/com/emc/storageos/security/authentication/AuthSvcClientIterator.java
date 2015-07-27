/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authentication;

import java.net.URI;

import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.security.helpers.ClientRequestHelper;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;


/**
 *  Public iterator that uses auth tokens to make calls to authsvc
 */
// GEO-TODO: rename this after the merge with the geo branch so it won't include Auth in
// the name
public class AuthSvcClientIterator extends AuthSvcBaseClientIterator {
    private static final Logger _log = LoggerFactory.getLogger(AuthSvcClientIterator.class);

    /**
     * Constructs the AuthSvcClientIterator using an AuthSvEndPointLocator
     * @param authSvcEndPointLocator
     */
    public AuthSvcClientIterator(EndPointLocator authSvcEndPointLocator) {
        super(authSvcEndPointLocator); 
        setClientRequestHelper(new ClientRequestHelper());
    }

    /**
     * Run a get request on the current URI in the list and advance the pointer,
     * using auth token as authentication instead of signature
     * @param uri
     * @param authToken authentication token
     * @return ClientResponse
     */
    public ClientResponse get(URI uri, String authToken) {   
        WebResource webResource = _clientHelper.createRequest(_client, _authSvcEndpoints[_currentIndex++], uri);
        WebResource.Builder bld = webResource.accept(MediaType.TEXT_PLAIN,MediaType.APPLICATION_XML,
                MediaType.APPLICATION_JSON);
        return _clientHelper.addToken(bld, authToken).get(ClientResponse.class);
    } 
}
