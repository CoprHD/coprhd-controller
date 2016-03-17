/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

import com.sun.jersey.api.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.net.URI;

/**
 * Generic REST client over HTTP
 */
public class RESTClient {
    private Client _client; 

    /**
     * Constructor
     * 
     * @param client Jersey client to use
     */
    public RESTClient(Client client) {
        _client = client;
    }

    /**
     * Get resource as a ClientResponse
     * 
     * @param url url for the resource to get
     * @return ClientResponse response
     */
    public ClientResponse get(URI url) {
        return _client.resource(url).get(ClientResponse.class);
    }

    /**
     * Get attribute information about resource.
     * 
     * @param url url for the resource to execute head
     * @return ClientResponse response
     */
    public ClientResponse head(URI url) {
        return _client.resource(url).head();
    }

    /**
     * Put resource at url
     * 
     * @param url url for the resource to put
     * @param queryParams if any parameters supplied
     * @param body body for the put
     * @return ClientResponse response
     */
    public ClientResponse put(URI url, MultivaluedMap<String, String> queryParams, String body) {
        WebResource r = _client.resource(url);
        if (queryParams != null && queryParams.size() > 0) {
            WebResource.Builder rb = r.queryParams(queryParams).header("x-isi-ifs-target-type", "container")
                    .header("x-isi-ifs-access-control", "0755");
            return rb.put(ClientResponse.class, body);
        } else {
            return r.header("x-isi-ifs-target-type", "container").header("x-isi-ifs-access-control", "0755")
                    .put(ClientResponse.class, body);
        }
    }

    /**
     * Delete resource at url
     * 
     * @param url url for the resource to delete
     * @return ClientResponse
     */
    public ClientResponse delete(URI url) {
        return _client.resource(url).delete(ClientResponse.class);
    }

    /**
     * Post resource at url
     * 
     * @param url url to post to
     * @param body content to post
     * @return ClientResponse response
     */
    public ClientResponse post(URI url, String body) {
        return _client.resource(url).type(MediaType.APPLICATION_JSON).post(ClientResponse.class, body);
    }

    /**
     * Close the client
     */
    public void close() {
        _client.destroy();
    }
}
