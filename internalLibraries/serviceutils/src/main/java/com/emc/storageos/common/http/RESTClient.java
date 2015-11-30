/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.common.http;

import java.net.URI;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;

import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache.ApacheHttpClient;

/**
 * Generic REST client over HTTP
 */
public class RESTClient implements RestClientItf {

    private ApacheHttpClient _client;

    public RESTClient(ApacheHttpClient client) {
        _client = client;
    }

    @Override
    public ClientResponse get(URI url) throws InternalException {
        return get(url, null, null, null, null);
    }

    @Override
    public ClientResponse get(URI url, String username, String password) {
        WebResource resource = _client.resource(url);
        addAuthFilterIfNecessary(username, password, resource);
        return resource.get(ClientResponse.class);
    }

    @Override
    public ClientResponse get(URI url, Map<String, String> headers) {
        return get(url, headers, null, null);
    }

    @Override
    public ClientResponse get(URI url, Map<String, String> headers, String username, String password) {
        return get(url, null, headers, username, password);
    }
    
    @Override
    public ClientResponse get(URI url, MultivaluedMap<String, String> queryParams) {
        return get(url, queryParams, null, null, null);
    }

    @Override
    public ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, String username, String password) {
        return get(url, queryParams, null, username, password);
    }

    @Override
    public ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers) {
        return get(url, queryParams, headers, null, null);
    }
    @Override
    public ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers, String username,
            String password) {
        WebResource r = _client.resource(url);
        if (username != null && password != null) {
            r.addFilter(new HTTPBasicAuthFilter(username, password));
        }
        if (queryParams != null && queryParams.size() > 0) {
            r = r.queryParams(queryParams);
        }

        WebResource.Builder rb = getBuilderWithHeaders(headers, r);
        return (rb != null) ? rb.get(ClientResponse.class) : r.put(ClientResponse.class);
    }

    @Override
    public ClientResponse put(URI uri, String body) throws InternalException {
        return put(uri, body, null, null);
    }

    @Override
    public ClientResponse put(URI url, String body, String username, String password) throws InternalException {
        WebResource r = _client.resource(url);
        if (username != null && password != null) {
            r.addFilter(new HTTPBasicAuthFilter(username, password));
        }
        return r.put(ClientResponse.class, body);
    }

    @Override
    public ClientResponse put(URI url, Map<String, String> headers, String body) {
        return put(url, headers, body, null, null);
    }

    @Override
    public ClientResponse put(URI url, Map<String, String> headers, String body, String username, String password) {
        return put(url, null, headers, body, username, password);
    }

    @Override
    public ClientResponse put(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers, String body) {
        return put(url, queryParams, headers, body, null, null);
    }

    @Override
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

    @Override
    public ClientResponse post(URI uri, String body) throws InternalException {
        return post(uri, body, null, null);
    }

    @Override
    public ClientResponse post(URI url, String body, String username, String password) throws InternalException {
        return post(url, null, body, username, password);
    }

    @Override
    public ClientResponse post(URI url, Map<String, String> headers, String body) {
        return post(url, headers, body, null, null);
    }

    @Override
    public ClientResponse post(URI url, Map<String, String> headers, String body, String username, String password) {
        WebResource r = _client.resource(url);

        addAuthFilterIfNecessary(username, password, r);

        WebResource.Builder rb = getBuilderWithHeaders(headers, r);
        return (rb != null) ? rb.post(ClientResponse.class, body) : r.post(ClientResponse.class, body);
    }

    @Override
    public ClientResponse delete(URI uri) throws InternalException {
        return delete(uri, null, null, null, null);
    }

    @Override
    public ClientResponse delete(URI url, String username, String password) {
        WebResource resource = _client.resource(url);
        addAuthFilterIfNecessary(username, password, resource);
        return resource.delete(ClientResponse.class);
    }

    @Override
    public ClientResponse delete(URI url, Map<String, String> headers) {
        return delete(url, headers, null, null);
    }
    
    public ClientResponse delete(URI url, Map<String, String> headers, String username, String password) {
        return delete(url, headers, null, username, password);
    }
    
    @Override
    public ClientResponse delete(URI uri, String body) throws InternalException {
        return delete(uri, null, body, null, null);
    }

    @Override
    public ClientResponse delete(URI url, String body, String username, String password) {
        return delete(url, null, body, username, password);
    }

    @Override
    public ClientResponse delete(URI url, Map<String, String> headers, String body) {
        return delete(url, headers, body, null, null);
    }

    @Override
    public ClientResponse delete(URI url, Map<String, String> headers, String body, String username, String password) {
        WebResource r = _client.resource(url);
        addAuthFilterIfNecessary(username, password, r);
        WebResource.Builder rb = getBuilderWithHeaders(headers, r);

        if (body != null)
            return (rb != null) ? rb.delete(ClientResponse.class) : r.delete(ClientResponse.class);
            else
                return (rb != null) ? rb.delete(ClientResponse.class, body) : r.delete(ClientResponse.class, body);
    }
    @Override
    public ClientResponse head(URI url) {
        return head(url, null, null);
    }

    @Override
    public ClientResponse head(URI url, String username, String password) {
        WebResource resource = _client.resource(url);
        addAuthFilterIfNecessary(username, password, resource);
        return resource.head();
    }

    private void addAuthFilterIfNecessary(String username, String password, WebResource resource) {
        if (username != null && password != null)
            resource.addFilter(new HTTPBasicAuthFilter(username, password));
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