/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.common.http;

import java.net.URI;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache.ApacheHttpClient;

/**
 * Generic REST client over HTTP
 */
public class RESTClient {
    private ApacheHttpClient _client;

    /**
     * Constructor
     * 
     * @param client Jersey client to use
     */
    public RESTClient(ApacheHttpClient client) {
        _client = client;
    }

    /**
     * Get resource as a ClientResponse
     * 
     * @param url url for the resource to get
     * @return ClientResponse response
     */
    public ClientResponse get(URI url, String username, String password) {
        WebResource resource = _client.resource(url);
        if (username != null && password != null)
            resource.addFilter(new HTTPBasicAuthFilter(username, password));
        return resource.get(ClientResponse.class);
    }

    /**
     * Get attribute information about resource.
     * 
     * @param url url for the resource to execute head
     * @return ClientResponse response
     */
    public ClientResponse head(URI url, String username, String password) {
        WebResource resource = _client.resource(url);
        if (username != null && password != null)
            resource.addFilter(new HTTPBasicAuthFilter(username, password));
        return resource.head();
    }

    /**
     * Put resource at url
     * 
     * @param url url for the resource to put
     * @param queryParams if any parameters supplied
     * @param body body for the put
     * @return ClientResponse response
     */
    public ClientResponse put(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers,
            String body, String username, String password) {
        WebResource r = _client.resource(url);
        if (username != null && password != null) {
            r.addFilter(new HTTPBasicAuthFilter(username, password));
        }
        if (queryParams != null && queryParams.size() > 0) {
            r = r.queryParams(queryParams);
        }

        WebResource.Builder rb = getBuilderWithHeaders(headers, r);
        return (rb != null) ? rb.put(ClientResponse.class, body) : r.put(ClientResponse.class, body);
    }

    public ClientResponse put(URI url, Map<String, String> headers, String body, String username, String password) {
        return put(url, null, headers, body, username, password);
    }

    private WebResource.Builder getBuilderWithHeaders(Map<String, String> headers, WebResource r) {
        WebResource.Builder rb = null;
        if (headers != null && headers.size() > 0)
            for (String headerKey : headers.keySet()) {
                rb = r.header(headerKey, headers.get(headerKey));
            }
        return rb;
    }

    /**
     * Delete resource at url
     * 
     * @param url url for the resource to delete
     * @return ClientResponse
     */
    public ClientResponse delete(URI url, String username, String password) {
        WebResource resource = _client.resource(url);
        if (username != null && password != null)
            resource.addFilter(new HTTPBasicAuthFilter(username, password));
        return resource.delete(ClientResponse.class);
    }

    
    public ClientResponse post(URI url,Map<String, String> headers, String body, String username, String password) {
        WebResource r = _client.resource(url);
        
        if (username != null && password != null)
            r.addFilter(new HTTPBasicAuthFilter(username, password));

        WebResource.Builder rb = getBuilderWithHeaders(headers, r);
        return (rb != null) ? rb.post(ClientResponse.class, body) : r.post(ClientResponse.class, body);
    }

    public ClientResponse get(URI url, Map<String, String> headers, String username, String password) {
        WebResource r = _client.resource(url);
        
        if (username != null && password != null)
            r.addFilter(new HTTPBasicAuthFilter(username, password));
        
        WebResource.Builder rb = getBuilderWithHeaders(headers, r);
        return (rb != null) ? rb.get(ClientResponse.class) : r.get(ClientResponse.class);
    }

    /**
     * Close the client
     */
    public void close() {
        _client.destroy();
    }

    public ApacheHttpClient getClient() {
        return _client;
    }

    public void setClient(ApacheHttpClient _client) {
        this._client = _client;
    }
}