/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */


package com.emc.storageos.xiv.api;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

public class RESTClient {

    private static Logger _log = LoggerFactory.getLogger(RESTClient.class);

    public static final int GET_RETRY_COUNT = 12;
    public static final long GET_SLEEP_TIME_MS = 5000;

    private Client _client;
    
    public RESTClient(Client client) {
        _client = client;
    }

    public RESTClient(Client client, String userName, String password) {
        _client = client;
        _client.addFilter(new HTTPBasicAuthFilter(userName, password));
    }

    public ClientResponse post(URI url, String body) {
        WebResource r = _client.resource(url);
        return r.header("Content-Type", "application/json").post(ClientResponse.class, body);
    }

    public ClientResponse put(URI url, String body) {
        WebResource r = _client.resource(url);
        return r.header("Content-Type", "application/json").put(ClientResponse.class, body);
    }

    public ClientResponse get(URI resourceURI) {
        WebResource r = _client.resource(resourceURI);
        return r.header("Content-Type", "application/json").get(ClientResponse.class);
    }
    
    public void close() {
        _client.destroy();
    }

}
