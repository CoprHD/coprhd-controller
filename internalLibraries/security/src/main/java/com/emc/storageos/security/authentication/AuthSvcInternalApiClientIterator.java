/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authentication;

import java.net.URI;

import javax.ws.rs.core.MediaType;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.helpers.ClientRequestHelper;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

/**
 * Iterator that uses signature to make internal api calls to authsvc
 */
// GEO-TODO: rename this after the merge with the geo branch so it won't include Auth in
// the name
public class AuthSvcInternalApiClientIterator extends AuthSvcBaseClientIterator {

    private int clientReadTimeout = 300 * 1000;
    private int clientConnectTimeout = 300 * 1000;

    /**
     * Constructor when using signature based api calls which will need a
     * signature using the coordinator
     * 
     * @param authSvcEndPointLocator
     * @param coordinator
     */
    public AuthSvcInternalApiClientIterator(EndPointLocator authSvcEndPointLocator,
            CoordinatorClient coordinator) {
        super(authSvcEndPointLocator);
        setClientRequestHelper(new ClientRequestHelper(coordinator, clientReadTimeout, clientConnectTimeout));
    }

    /**
     * Run a get request on the current URI in the list and advance the pointer
     * 
     * @param uri
     * @return
     */
    public ClientResponse get(URI uri) {
        WebResource webResource = _clientHelper.createRequest(_client, _authSvcEndpoints[_currentIndex++], uri);
        return _clientHelper.addSignature(webResource).accept(MediaType.TEXT_PLAIN, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON).
                get(ClientResponse.class);
    }

    public ClientResponse post(URI uri, Object requestBody) {
        WebResource webResource = _clientHelper.createRequest(_client, _authSvcEndpoints[_currentIndex++], uri);
        return _clientHelper.addSignature(webResource).accept(MediaType.TEXT_PLAIN, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON).
                post(ClientResponse.class, requestBody);
    }

    /**
     * Run a put request on the current URI in the list and advance the pointer, using
     * auth token as authentication instead of signature
     * 
     * @param uri
     * @param authToken authentication token
     * @return ClientResponse
     * 
     */
    public ClientResponse put(URI uri, Object requestBody) {
        WebResource webResource = _clientHelper.createRequest(_client, _authSvcEndpoints[_currentIndex++], uri);
        return _clientHelper.addSignature(webResource).accept(MediaType.TEXT_PLAIN, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON).
                put(ClientResponse.class, requestBody);
    }

}
